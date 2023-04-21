/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.ballerinalang.compiler.semantics.analyzer;

import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.tree.IdentifierNode;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.parser.BLangAnonymousModelHelper;
import org.wso2.ballerinalang.compiler.parser.BLangMissingNodesHelper;
import org.wso2.ballerinalang.compiler.semantics.model.BTypeDefinition;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.*;
import org.wso2.ballerinalang.compiler.semantics.model.types.*;
import org.wso2.ballerinalang.compiler.tree.*;
import org.wso2.ballerinalang.compiler.tree.expressions.*;
import org.wso2.ballerinalang.compiler.tree.types.*;
import org.wso2.ballerinalang.compiler.util.*;
import org.wso2.ballerinalang.util.Flags;

import java.util.*;
import java.util.stream.Collectors;

import static org.ballerinalang.model.symbols.SymbolOrigin.*;
import static org.wso2.ballerinalang.compiler.util.Constants.INFERRED_ARRAY_INDICATOR;
import static org.wso2.ballerinalang.compiler.util.Constants.OPEN_ARRAY_INDICATOR;

/**
 * @since 2201.4.0
 */

public class TypeResolver {

    private static final CompilerContext.Key<TypeResolver> TYPE_RESOLVER_KEY = new CompilerContext.Key<>();

    private final SymbolTable symTable;
    private final Names names;
    private final SymbolResolver symResolver;
    private final SymbolEnter symEnter;
    private final BLangDiagnosticLog dlog;
    private final Types types;
    private int typePrecedence;
    private final TypeParamAnalyzer typeParamAnalyzer;
    private final ConstantTypeChecker constantTypeChecker;
    private final ConstantTypeChecker.ResolveConstantExpressionType resolveConstantExpressionType;
    private final ImmutableTypeCloner.UpdateImmutableType updateImmutableType;
    private BLangAnonymousModelHelper anonymousModelHelper;
    private BLangMissingNodesHelper missingNodesHelper;

    private List<BLangTypeDefinition> resolvingtypeDefinitions = new ArrayList<>();
    private HashMap<BIntersectionType, BLangIntersectionTypeNode> intersectionTypeList;
    public HashSet<BLangConstant> resolvedConstants = new HashSet<>();
    private HashSet<BLangConstant> resolvingConstants = new HashSet<>();
    private HashSet<BLangClassDefinition> resolvedClassDef = new HashSet<>();
    private Map<String, BLangNode> modTable = new LinkedHashMap<>();
    private Map<String, BLangConstantValue> constantMap = new HashMap<>();
    private HashSet<LocationData> unknownTypeRefs;
    private SymbolEnv pkgEnv;
    private int currentDepth;
    private Stack<BType> unresolvedTypes;
    public HashSet<BStructureType> resolvingStructureTypes = new HashSet<>();

    public TypeResolver(CompilerContext context) {
        context.put(TYPE_RESOLVER_KEY, this);

        this.symTable = SymbolTable.getInstance(context);
        this.symEnter = SymbolEnter.getInstance(context);
        this.names = Names.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.types = Types.getInstance(context);
        this.typeParamAnalyzer = TypeParamAnalyzer.getInstance(context);
        this.anonymousModelHelper = BLangAnonymousModelHelper.getInstance(context);
        this.missingNodesHelper = BLangMissingNodesHelper.getInstance(context);
        this.constantTypeChecker = ConstantTypeChecker.getInstance(context);
        this.resolveConstantExpressionType = ConstantTypeChecker.ResolveConstantExpressionType.getInstance(context);
        this.updateImmutableType = ImmutableTypeCloner.UpdateImmutableType.getInstance(context);
        this.unknownTypeRefs = new HashSet<>();
    }

    private void cleanup() {
        unknownTypeRefs.clear();
    }

    public static TypeResolver getInstance(CompilerContext context) {
        TypeResolver typeResolver = context.get(TYPE_RESOLVER_KEY);
        if (typeResolver == null) {
            typeResolver = new TypeResolver(context);
        }

        return typeResolver;
    }

    public void defineBTypes(List<BLangNode> moduleDefs, SymbolEnv pkgEnv) {
        this.pkgEnv = pkgEnv;
        typePrecedence = 0;
        for (BLangNode typeAndClassDef : moduleDefs) {
            String typeOrClassName = symEnter.getTypeOrClassName(typeAndClassDef);
            if (!modTable.containsKey(typeOrClassName)) {
                modTable.put(typeOrClassName, typeAndClassDef);
            }
        }

        for (BLangNode def : moduleDefs) {
            unresolvedTypes = new Stack<>();
            if (def.getKind() == NodeKind.CLASS_DEFN) {
                intersectionTypeList = new HashMap<>();
                extracted(pkgEnv, modTable, (BLangClassDefinition) def);
                fillEffectiveTypeOfIntersectionTypes(pkgEnv);
            } else if (def.getKind() == NodeKind.CONSTANT) {
                resolveConstant(pkgEnv, modTable, (BLangConstant) def);
            } else {
                BLangTypeDefinition typeDefinition = (BLangTypeDefinition) def;
                intersectionTypeList = new HashMap<>();
                resolveTypeDefinition(pkgEnv, modTable, typeDefinition, 0);
                BType type = typeDefinition.typeNode.getBType();
                if (typeDefinition.hasCyclicReference) {
                    updateIsCyclicFlag(type);
                }
                fillEffectiveTypeOfIntersectionTypes(pkgEnv);
                handleDistinctDefinitionOfErrorIntersection(typeDefinition, type);
            }
        }
        modTable.clear();
        cleanup();
    }

    private BType extracted(SymbolEnv pkgEnv, Map<String, BLangNode> modTable, BLangClassDefinition classDefinition) {
        if (resolvedClassDef.contains(classDefinition)) {
            return classDefinition.getBType();
        }

        if (classDefinition.getBType() != null) {
            return classDefinition.getBType();
        }

        defineClassDef(classDefinition, pkgEnv);
        symEnter.defineDistinctClassAndObjectDefinitionIndividual(classDefinition);

        // Define the class fields
        defineField(classDefinition, modTable, pkgEnv);
        resolvedClassDef.add(classDefinition);
        BObjectType classDefType = (BObjectType) classDefinition.getBType();
        resolvingStructureTypes.remove(classDefType);

        classDefinition.setPrecedence(this.typePrecedence++);
        return classDefType;
    }

    public void defineField(BLangNode typeDefNode, Map<String, BLangNode> mod, SymbolEnv pkgEn) {
        if (typeDefNode.getKind() == NodeKind.CLASS_DEFN) {
            BLangClassDefinition classDefinition = (BLangClassDefinition) typeDefNode;
            if (symEnter.isObjectCtor(classDefinition)) {
                return;
            }
            defineFieldsOfClassDef(classDefinition, mod, pkgEn);
            symEnter.defineReferencedFieldsOfClassDef(classDefinition, pkgEn);
        } else if (typeDefNode.getKind() == NodeKind.TYPE_DEFINITION) {
            symEnter.defineFields((BLangTypeDefinition) typeDefNode, pkgEn);
            symEnter.defineReferencedFieldsOfRecordTypeDef((BLangTypeDefinition) typeDefNode);
        }
    }

    public void defineFieldsOfClassDef(BLangClassDefinition classDefinition, Map<String, BLangNode> mod, SymbolEnv env) {
        SymbolEnv typeDefEnv = SymbolEnv.createClassEnv(classDefinition, classDefinition.symbol.scope, env);
        BObjectTypeSymbol tSymbol = (BObjectTypeSymbol) classDefinition.symbol;
        BObjectType objType = (BObjectType) tSymbol.type;

        if (classDefinition.isObjectContructorDecl) {
            classDefinition.oceEnvData.fieldEnv = typeDefEnv;
        }

        classDefinition.typeDefEnv = typeDefEnv;

        for (BLangSimpleVariable field : classDefinition.fields) {
            symEnter.defineNode(field, typeDefEnv);
            if (field.expr != null) {
                field.symbol.isDefaultable = true;
            }
            // Unless skipped, this causes issues in negative cases such as duplicate fields.
            if (field.symbol.type == symTable.semanticError) {
                continue;
            }
            objType.fields.put(field.name.value, new BField(names.fromIdNode(field.name), field.pos, field.symbol));
        }
    }

    private void defineRestFields(BLangTypeDefinition typeDef) {
        BStructureType structureType = (BStructureType) typeDef.symbol.type;
        BLangStructureTypeNode structureTypeNode = (BLangStructureTypeNode) typeDef.typeNode;
        if (typeDef.symbol.kind == SymbolKind.TYPE_DEF && structureType.tsymbol.kind != SymbolKind.RECORD) {
            return;
        }

        SymbolEnv typeDefEnv = structureTypeNode.typeDefEnv;
        BLangRecordTypeNode recordTypeNode = (BLangRecordTypeNode) structureTypeNode;
        BRecordType recordType = (BRecordType) structureType;
        recordType.sealed = recordTypeNode.sealed;
        if (recordTypeNode.sealed && recordTypeNode.restFieldType != null) {
            dlog.error(recordTypeNode.restFieldType.pos, DiagnosticErrorCode.REST_FIELD_NOT_ALLOWED_IN_CLOSED_RECORDS);
            return;
        }

        if (recordTypeNode.restFieldType != null) {
            recordType.restFieldType = symResolver.resolveTypeNode(recordTypeNode.restFieldType, typeDefEnv);
            return;
        }

        if (!recordTypeNode.sealed) {
            recordType.restFieldType = symTable.anydataType;
            return;
        }
    }

    public void defineClassDef(BLangClassDefinition classDefinition, SymbolEnv env) {
        EnumSet<Flag> flags = EnumSet.copyOf(classDefinition.flagSet);
        boolean isPublicType = flags.contains(Flag.PUBLIC);
        Name className = names.fromIdNode(classDefinition.name);
        Name classOrigName = names.originalNameFromIdNode(classDefinition.name);

        BClassSymbol tSymbol = Symbols.createClassSymbol(Flags.asMask(flags), className, env.enclPkg.symbol.pkgID, null,
                env.scope.owner, classDefinition.name.pos,
                symEnter.getOrigin(className, flags), classDefinition.isServiceDecl);
        tSymbol.originalName = classOrigName;
        tSymbol.scope = new Scope(tSymbol);
        tSymbol.markdownDocumentation = symEnter.getMarkdownDocAttachment(classDefinition.markdownDocumentationAttachment);


        long typeFlags = 0;

        if (flags.contains(Flag.READONLY)) {
            typeFlags |= Flags.READONLY;
        }

        if (flags.contains(Flag.ISOLATED)) {
            typeFlags |= Flags.ISOLATED;
        }

        if (flags.contains(Flag.SERVICE)) {
            typeFlags |= Flags.SERVICE;
        }

        if (flags.contains(Flag.OBJECT_CTOR)) {
            typeFlags |= Flags.OBJECT_CTOR;
        }

        BObjectType objectType = new BObjectType(tSymbol, typeFlags);
        resolvingStructureTypes.add(objectType);
        if (classDefinition.isObjectContructorDecl || flags.contains(Flag.OBJECT_CTOR)) {
            classDefinition.oceEnvData.objectType = objectType;
            objectType.classDef = classDefinition;
        }

        if (flags.contains(Flag.DISTINCT)) {
            objectType.typeIdSet = BTypeIdSet.from(env.enclPkg.symbol.pkgID, classDefinition.name.value, isPublicType);
        }

        if (flags.contains(Flag.CLIENT)) {
            objectType.flags |= Flags.CLIENT;
        }

        tSymbol.type = objectType;
        classDefinition.setBType(objectType);
        classDefinition.setDeterminedType(objectType);
        classDefinition.symbol = tSymbol;

        if (symEnter.isDeprecated(classDefinition.annAttachments)) {
            tSymbol.flags |= Flags.DEPRECATED;
        }

        if (symResolver.checkForUniqueSymbol(classDefinition.pos, env, tSymbol)) {
            env.scope.define(tSymbol.name, tSymbol);
        }

        // For each referenced type, check whether the types are already resolved.
        // If not, then that type should get a higher precedence.
        for (BLangType typeRef : classDefinition.typeRefs) {
            BType referencedType = symResolver.resolveTypeNode(typeRef, env);
            objectType.typeInclusions.add(referencedType);
        }

        // TODO : check
        // env.scope.define(tSymbol.name, tSymbol);
    }


    private void fillEffectiveTypeOfIntersectionTypes(SymbolEnv symEnv) {
        for (BIntersectionType intersectionType: intersectionTypeList.keySet()) {
                BLangIntersectionTypeNode intersectionTypeNode = intersectionTypeList.get(intersectionType);
                updateImmutableType.updateImmutableType(intersectionType, symTable.builtinPos,
                        symTable.rootPkgSymbol.pkgID, intersectionTypeNode, symEnv);
//                ImmutableTypeCloner.updateImmutableType(intersectionType, symTable.builtinPos,
//                        symTable.rootPkgSymbol.pkgID, intersectionTypeNode, types, symEnv, symTable,
//                        anonymousModelHelper, names);
        }
    }

    private BType calculateEffectiveType(BLangIdentifier name, BLangType typeNode, BLangType bLangTypeOne,
                                         BLangType bLangTypeTwo, BType typeOne, BType typeTwo) {
        BType typeOneReference = Types.getReferredType(typeOne);
        BType typeTwoReference = Types.getReferredType(typeTwo);

        if (typeOneReference.tag != TypeTags.ERROR || typeTwoReference.tag != TypeTags.ERROR) {
            dlog.error(typeNode.pos,
                    DiagnosticErrorCode.UNSUPPORTED_TYPE_INTERSECTION);
            return symTable.semanticError;
        }

        BType potentialIntersectionType = getPotentialIntersection(
                Types.IntersectionContext.from(dlog, bLangTypeOne.pos, bLangTypeTwo.pos),
                typeOne, typeTwo, pkgEnv);

        if (potentialIntersectionType.tag == TypeTags.SEMANTIC_ERROR) {
            dlog.error(typeNode.pos, DiagnosticErrorCode.INVALID_INTERSECTION_TYPE, typeNode);
            return symTable.semanticError;
        }

        symEnter.lookupTypeSymbol(pkgEnv, name).type = potentialIntersectionType;
        return potentialIntersectionType;
    }

    private BType getPotentialIntersection(Types.IntersectionContext intersectionContext,
                                           BType lhsType, BType rhsType, SymbolEnv env) {
        if (lhsType == symTable.readonlyType) {
            return rhsType;
        }

        if (rhsType == symTable.readonlyType) {
            return lhsType;
        }

        return types.getTypeIntersection(intersectionContext, lhsType, rhsType, env);
    }

    private void handleDistinctDefinitionOfErrorIntersection(BLangTypeDefinition typeDefinition,
                                                             BType definedType) {
        BType referenceConstraintType = Types.getReferredType(definedType);
        BSymbol typeDefSymbol = typeDefinition.symbol;
        if (typeDefinition.typeNode.flagSet.contains(Flag.DISTINCT)) {
            if (referenceConstraintType.tag == TypeTags.INTERSECTION &&
                    ((BIntersectionType) referenceConstraintType).effectiveType.getKind() == TypeKind.ERROR) {
                boolean distinctFlagPresentInTypeDef = typeDefinition.typeNode.flagSet.contains(Flag.DISTINCT);

                BTypeIdSet typeIdSet = BTypeIdSet.emptySet();
                int numberOfDistinctConstituentTypes = 0;
                BLangIntersectionTypeNode intersectionTypeNode = (BLangIntersectionTypeNode) typeDefinition.typeNode;
                for (BLangType constituentType : intersectionTypeNode.constituentTypeNodes) {
                    BType type = Types.getReferredType(constituentType.getBType());

                    if (type.getKind() == TypeKind.ERROR) {
                        if (constituentType.flagSet.contains(Flag.DISTINCT)) {
                            numberOfDistinctConstituentTypes++;
                            typeIdSet.addSecondarySet(((BErrorType) type).typeIdSet.getAll());
                        } else {
                            typeIdSet.add(((BErrorType) type).typeIdSet);
                        }
                    }
                }

                BErrorType effectiveType = (BErrorType) ((BIntersectionType) referenceConstraintType).effectiveType;

                // if the distinct keyword is part of a distinct-type-descriptor that is the
                // only distinct-type-descriptor occurring within a module-type-defn,
                // then the local id is the name of the type defined by the module-type-defn.
                if (numberOfDistinctConstituentTypes == 1
                        || (numberOfDistinctConstituentTypes == 0 && distinctFlagPresentInTypeDef)) {
                    effectiveType.typeIdSet = BTypeIdSet.from(pkgEnv.enclPkg.packageID, typeDefinition.name.value,
                            true, typeIdSet);
                } else {
                    for (BLangType constituentType : intersectionTypeNode.constituentTypeNodes) {
                        if (constituentType.flagSet.contains(Flag.DISTINCT)) {
                            typeIdSet.add(BTypeIdSet.from(pkgEnv.enclPkg.packageID,
                                    anonymousModelHelper.getNextAnonymousTypeId(pkgEnv.enclPkg.packageID), true));
                        }
                    }
                    effectiveType.typeIdSet = typeIdSet;
                }

                //setting the newly created distinct type as the referred type of the definition
                if (((BTypeDefinitionSymbol) typeDefSymbol).referenceType != null) {
                    ((BTypeDefinitionSymbol) typeDefSymbol).referenceType.referredType = definedType;
                }
                definedType.flags |= Flags.DISTINCT;
            }
        }
    }

    private BType resolveTypeDefinition(SymbolEnv symEnv, Map<String, BLangNode> mod, BLangTypeDefinition defn, int depth) {
        if (defn.getBType() != null) {
            // Already defined.
            return defn.getBType();
        }

        if (depth == defn.cycleDepth) {
            // We cannot define recursive typeDefinitions with same depths.
            dlog.error(defn.pos, DiagnosticErrorCode.CYCLIC_TYPE_REFERENCE, defn.name);
            return symTable.semanticError;
        }

        currentDepth = depth;
        defn.cycleDepth = depth;
        boolean hasAlreadyVisited = false;

        if (resolvingtypeDefinitions.contains(defn)) {
            // Type definition has a cyclic reference.
            for (int i = resolvingtypeDefinitions.size() - 1; i >= 0; i--) {
                resolvingtypeDefinitions.get(i).hasCyclicReference = true;
                if (resolvingtypeDefinitions.get(i) == defn) {
                    break;
                }
            }
            hasAlreadyVisited = true;
        } else {
            // Add the type into resolvingtypeDefinitions list.
            // This is used to identify types which have cyclic references.
            resolvingtypeDefinitions.add(defn);
        }

        // Resolve the type
        BType type = resolveTypeDesc(symEnv, mod, defn, depth, defn.typeNode);

        // Define the typeDefinition. Add symbol, flags etc.
        type = defineTypeDefinition(defn, type, symEnv);
//        symEnter.populateDistinctTypeIdsFromIncludedTypeReferences(defn); // disable due to intersections

        if (!hasAlreadyVisited) {
            // Remove the typeDefinition from currently resolving typeDefinition map.
            resolvingtypeDefinitions.remove(defn);
        }

        if (defn.getBType() == null) {
            defn.setBType(type);
            defn.cycleDepth = -1;
        }
        return type;
    }

    private void updateIsCyclicFlag(BType type) {
        if (type == null) {
            return;
        }
        switch (type.getKind()) {
            case TUPLE:
                ((BTupleType) type).isCyclic = true;
                break;
            case UNION:
                ((BUnionType) type).isCyclic = true;
                break;
            case INTERSECTION:
                updateIsCyclicFlag(((BIntersectionType) type).getEffectiveType());
                break;
        }
    }

    private void logErrorForCyclicMapAndArray(BType type) {
        if (type == null) {
            return;
        }
        switch (type.getKind()) {
            case ARRAY:
            case MAP:
                dlog.error(type.tsymbol.pos, DiagnosticErrorCode.CYCLIC_TYPE_REFERENCE, type.name);
                break;
        }
    }

    public BType validateModuleLevelDef(String name, Name pkgAlias, Name typeName, BLangUserDefinedType td) {
        BLangNode moduleLevelDef = modTable.get(name);
        if (moduleLevelDef == null) {
            if (missingNodesHelper.isMissingNode(pkgAlias) || missingNodesHelper.isMissingNode(typeName)) {
                return symTable.semanticError;
            }

            Location pos = td.pos;
            LocationData locationData = new LocationData(name, pos.lineRange().startLine().line(),
                    pos.lineRange().startLine().offset());
            if (unknownTypeRefs.add(locationData)) {
                dlog.error(pos, DiagnosticErrorCode.UNKNOWN_TYPE, name);
            }
            return null;
        }
        if (moduleLevelDef.getKind() == NodeKind.TYPE_DEFINITION) {
            BLangTypeDefinition typeDefinition = (BLangTypeDefinition) moduleLevelDef;
            BType resolvedType = resolveTypeDefinition(pkgEnv, modTable, typeDefinition, currentDepth);
//            symEnter.populateDistinctTypeIdsFromIncludedTypeReferences(typeDefinition); // temporary disable due to intersections
            if (resolvedType == symTable.semanticError || resolvedType == symTable.noType) {
                return resolvedType;
            }
            BSymbol symbol = typeDefinition.symbol;
            td.symbol = symbol;
            if (symbol.kind == SymbolKind.TYPE_DEF && !Symbols.isFlagOn(symbol.flags, Flags.ANONYMOUS)) {
                BType referenceType = ((BTypeDefinitionSymbol) symbol).referenceType;
                referenceType.flags |= symbol.type.flags;
                referenceType.tsymbol.flags |= symbol.type.flags;
                return referenceType;
            }
            return resolvedType;
        } else if (moduleLevelDef.getKind() == NodeKind.CONSTANT) {
            BLangConstant constant = (BLangConstant) moduleLevelDef;
            return resolveTypeDefinition(pkgEnv, modTable, constant.associatedTypeDefinition, currentDepth);
        } else {
            return extracted(pkgEnv, modTable, (BLangClassDefinition) moduleLevelDef);
        }
    }

    public List<BLangSimpleVariable> getFieldsOfStructureType(String name) {
        BLangNode moduleLevelDef = modTable.get(name);
        if (moduleLevelDef != null && moduleLevelDef.getKind() == NodeKind.TYPE_DEFINITION) {
            BLangNode typeNode = ((BLangTypeDefinition) moduleLevelDef).typeNode;
            if (typeNode.getKind() == NodeKind.RECORD_TYPE || typeNode.getKind() == NodeKind.OBJECT_TYPE) {
                BLangStructureTypeNode structureTypeNode = (BLangStructureTypeNode) typeNode;
                return structureTypeNode.fields;
            }
        }
        return new ArrayList<>();
    }

    public BLangTypeDefinition getTypeDefinition(String name) {
        BLangNode moduleLevelDef = modTable.get(name);
        if (moduleLevelDef != null && moduleLevelDef.getKind() == NodeKind.TYPE_DEFINITION) {
            return (BLangTypeDefinition) moduleLevelDef;
        }
        return null;
    }

    private BType resolveTypeDesc(SymbolEnv symEnv, Map<String, BLangNode> mod, BLangTypeDefinition defn, int depth,
                                    BLangType td) {
        if (td == null) {
            return symTable.semanticError;
        }
        BType resultType;
        switch (td.getKind()) {
            case VALUE_TYPE:
                resultType = resolveTypeDesc((BLangValueType) td, symEnv);
                break;
            case CONSTRAINED_TYPE: // map<?> and typedesc<?>
                resultType = resolveTypeDesc((BLangConstrainedType) td, symEnv, mod, depth, defn);
                break;
            case ARRAY_TYPE:
                resultType = resolveTypeDesc(((BLangArrayType) td), symEnv, mod, depth, defn);
                break;
            case TUPLE_TYPE_NODE:
                resultType = resolveTypeDesc((BLangTupleTypeNode) td, symEnv, mod, depth, defn);
                break;
            case RECORD_TYPE:
                resultType = resolveTypeDesc((BLangRecordTypeNode) td, symEnv, mod, depth, defn);
                break;
            case OBJECT_TYPE: // Need to implement
                resultType = resolveTypeDesc((BLangObjectTypeNode) td, symEnv, mod, depth, defn);
                break;
            case FUNCTION_TYPE:
                resultType = resolveTypeDesc((BLangFunctionTypeNode) td, symEnv, mod, depth, defn);
                break;
            case ERROR_TYPE:
                resultType = resolveTypeDesc((BLangErrorType) td, symEnv, mod, depth, defn);
                break;
            case UNION_TYPE_NODE:
                resultType = resolveTypeDesc((BLangUnionTypeNode) td, symEnv, mod, depth, defn);
                break;
            case INTERSECTION_TYPE_NODE:
                resultType = resolveTypeDesc((BLangIntersectionTypeNode) td, symEnv, mod, depth, defn);
                break;
            case USER_DEFINED_TYPE:
                resultType = resolveTypeDesc((BLangUserDefinedType) td, symEnv, mod, depth);
                break;
            case BUILT_IN_REF_TYPE:
                resultType = resolveTypeDesc((BLangBuiltInRefTypeNode) td, symEnv);
                break;
            case FINITE_TYPE_NODE:
                resultType = resolveSingletonType((BLangFiniteTypeNode) td, symEnv);
                break;
            case TABLE_TYPE:
                resultType = resolveTypeDesc((BLangTableTypeNode) td, symEnv, mod, depth, defn);
                break;
            case STREAM_TYPE:
                resultType = resolveTypeDesc((BLangStreamType) td, symEnv, mod, depth, defn);
                break;
            default:
                throw new AssertionError("Invalid type");
        }

        BType refType = Types.getReferredType(resultType);
        if (refType != symTable.noType) {
            // If the typeNode.nullable is true then convert the resultType to a union type
            // if it is not already a union type, JSON type, or any type
            if (td.nullable && resultType.tag == TypeTags.UNION) {
                BUnionType unionType = (BUnionType) refType;
                unionType.add(symTable.nilType);
            }
        }

        symResolver.validateDistinctType(td, resultType);
        if (td.getBType() == null) {
            td.setBType(resultType);
        }
        return resultType;
    }

    private BType resolveTypeDesc(BLangValueType td, SymbolEnv symEnv) {
        SymbolResolver.AnalyzerData data = new SymbolResolver.AnalyzerData(symEnv);
        return visitBuiltInTypeNode(td, data, td.typeKind);
    }

    private BType resolveTypeDesc(BLangConstrainedType td, SymbolEnv symEnv, Map<String, BLangNode> mod,
                                  int depth, BLangTypeDefinition defn) {
        currentDepth = depth;
        TypeKind typeKind = ((BLangBuiltInRefTypeNode) td.getType()).getTypeKind();

        if (typeKind == TypeKind.MAP) {
            return resolveMapTypeDesc(td, symEnv, mod, depth, defn);
        } else if (typeKind == TypeKind.XML) {
            return resolveXmlTypeDesc(td, symEnv, mod, depth, defn);
        }

        BType type = resolveTypeDesc(symEnv, mod, defn, depth + 1, td.type);
        BType constraintType = resolveTypeDesc(symEnv, mod, defn, depth + 1, td.constraint);

        BType constrainedType;
        switch (typeKind) {
            case FUTURE:
                constrainedType = new BFutureType(TypeTags.FUTURE, constraintType, null);
                break;
            case TYPEDESC:
                constrainedType = new BTypedescType(constraintType, null);
                break;
            default:
                return symTable.neverType;
        }

        BTypeSymbol typeSymbol = type.tsymbol;
        constrainedType.tsymbol = Symbols.createTypeSymbol(typeSymbol.tag, typeSymbol.flags, typeSymbol.name,
                typeSymbol.originalName, typeSymbol.pkgID, constrainedType, typeSymbol.owner,
                td.pos, BUILTIN);
        symResolver.markParameterizedType(constrainedType, constraintType);
        td.setBType(constrainedType);
        return constrainedType;
    }

    private BType resolveXmlTypeDesc(BLangConstrainedType td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                       BLangTypeDefinition typeDefinition) {
        if (td.defn != null) {
            return td.defn.getImmutableType();
        }

        BType type = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.type);
        BType constrainedType = new BXMLType(null, null);
        BTypeSymbol typeSymbol = type.tsymbol;
        constrainedType.tsymbol = Symbols.createTypeSymbol(typeSymbol.tag, typeSymbol.flags, typeSymbol.name,
                typeSymbol.originalName, typeSymbol.pkgID, constrainedType, typeSymbol.owner,
                td.pos, BUILTIN);

        BTypeDefinition defn = new BTypeDefinition(constrainedType);
        td.defn = defn;
        td.type.setBType(constrainedType);

        BType constraintType = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.constraint);

        if (constraintType.tag == TypeTags.PARAMETERIZED_TYPE) {
            BType typedescType = ((BParameterizedType) constraintType).paramSymbol.type;
            BType typedescConstraint = ((BTypedescType) typedescType).constraint;
            symResolver.validateXMLConstraintType(typedescConstraint, td.pos);
        } else {
            symResolver.validateXMLConstraintType(constraintType, td.pos);
        }

        ((BXMLType) constrainedType).constraint = constraintType;
        symResolver.markParameterizedType(constrainedType, constraintType);
        return constrainedType;
    }

    private BType resolveMapTypeDesc(BLangConstrainedType td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                     BLangTypeDefinition typeDefinition) {
        if (td.defn != null) {
            return td.defn.getMutableType();
        }

        BType type = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.type);
        BTypeSymbol typeSymbol = type.tsymbol;
        BTypeSymbol tSymbol = Symbols.createTypeSymbol(SymTag.TYPE, typeSymbol.flags, Names.EMPTY,
                typeSymbol.originalName, symEnv.enclPkg.symbol.pkgID, null, symEnv.scope.owner,
                td.pos, BUILTIN);
        BType constrainedType = new BMapType(TypeTags.MAP, null, tSymbol);
        BTypeDefinition defn = new BTypeDefinition(constrainedType);
        td.defn = defn;
        td.type.setBType(constrainedType);
        tSymbol.type = type;

        BType constraintType = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.constraint);

        ((BMapType) constrainedType).constraint = constraintType;
        symResolver.markParameterizedType(constrainedType, constraintType);
        return constrainedType;
    }

    private BType resolveTypeDesc(BLangArrayType td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;
        if (td.defn != null) {
            return td.defn.getMutableType();
        }

        BType resultType = null;
        boolean isError = false;
        BArrayType firstDimArrType = null;
        boolean firstDim = true;

        for (int i = 0; i < td.dimensions; i++) {
            BTypeSymbol arrayTypeSymbol = Symbols.createTypeSymbol(SymTag.ARRAY_TYPE, Flags.PUBLIC, Names.EMPTY,
                    symEnv.enclPkg.symbol.pkgID, null, symEnv.scope.owner, td.pos, BUILTIN);
            BArrayType arrType;
            if (td.sizes.size() == 0) {
                arrType = new BArrayType(resultType, arrayTypeSymbol);
            } else {
                BLangExpression size = td.sizes.get(i);
                if (size.getKind() == NodeKind.LITERAL || size.getKind() == NodeKind.NUMERIC_LITERAL) {
                    Integer sizeIndicator = (Integer) (((BLangLiteral) size).getValue());
                    BArrayState arrayState;
                    if (sizeIndicator == OPEN_ARRAY_INDICATOR) {
                        arrayState = BArrayState.OPEN;
                    } else if (sizeIndicator == INFERRED_ARRAY_INDICATOR) {
                        arrayState = BArrayState.INFERRED;
                    } else {
                        arrayState = BArrayState.CLOSED;
                    }
                    arrType = new BArrayType(resultType, arrayTypeSymbol, sizeIndicator, arrayState);
                } else {
                    if (size.getKind() != NodeKind.SIMPLE_VARIABLE_REF) {
                        dlog.error(size.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, symTable.intType,
                                ((BLangTypedescExpr) size).getTypeNode());
                        isError = true;
                        continue;
                    }

                    BLangSimpleVarRef sizeReference = (BLangSimpleVarRef) size;
                    Name pkgAlias = names.fromIdNode(sizeReference.pkgAlias);
                    Name typeName = names.fromIdNode(sizeReference.variableName);

                    BSymbol sizeSymbol = symResolver.lookupMainSpaceSymbolInPackage(size.pos, symEnv, pkgAlias, typeName);
                    sizeReference.symbol = sizeSymbol;

                    if (symTable.notFoundSymbol == sizeSymbol) {
                        dlog.error(td.pos, DiagnosticErrorCode.UNDEFINED_SYMBOL, size);
                        isError = true;
                        continue;
                    }

                    if (sizeSymbol.tag != SymTag.CONSTANT) {
                        dlog.error(size.pos, DiagnosticErrorCode.INVALID_ARRAY_SIZE_REFERENCE, sizeSymbol);
                        isError = true;
                        continue;
                    }

                    BConstantSymbol sizeConstSymbol = (BConstantSymbol) sizeSymbol;
                    BType lengthLiteralType = sizeConstSymbol.literalType;

                    if (lengthLiteralType.tag != TypeTags.INT) {
                        dlog.error(size.pos, DiagnosticErrorCode.INCOMPATIBLE_TYPES, symTable.intType,
                                sizeConstSymbol.literalType);
                        isError = true;
                        continue;
                    }

                    int length;
                    long lengthCheck = Long.parseLong(sizeConstSymbol.type.toString());
                    if (lengthCheck > symResolver.MAX_ARRAY_SIZE) {
                        length = 0;
                        dlog.error(size.pos,
                                DiagnosticErrorCode.ARRAY_LENGTH_GREATER_THAT_2147483637_NOT_YET_SUPPORTED);
                    } else if (lengthCheck < 0) {
                        length = 0;
                        dlog.error(size.pos, DiagnosticErrorCode.INVALID_ARRAY_LENGTH);
                    } else {
                        length = (int) lengthCheck;
                    }
                    arrType = new BArrayType(resultType, arrayTypeSymbol, length, BArrayState.CLOSED);
                }
            }
            arrayTypeSymbol.type = arrType;
            resultType = arrayTypeSymbol.type;
            if (firstDim) {
                firstDimArrType = arrType;
                firstDim = false;
                continue;
            }
            symResolver.markParameterizedType(arrType, arrType.eType);
        }

//        BIntersectionType immutableType = new BIntersectionType(null, null, null);

        BTypeDefinition defn = new BTypeDefinition(resultType);
//        defn.setImmutableType(immutableType);
        td.defn = defn;
        td.setBType(resultType);
        unresolvedTypes.push(resultType);

        if (isError) {
            return symTable.semanticError;
        }

        firstDimArrType.eType = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.elemtype);
        symResolver.markParameterizedType(firstDimArrType, firstDimArrType.eType);
        unresolvedTypes.pop();
        return resultType;
    }

    private BType resolveTypeDesc(BLangTupleTypeNode td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;
        if (td.defn != null) {
            return td.defn.getMutableType();
        }

        BTypeSymbol tupleTypeSymbol = Symbols.createTypeSymbol(SymTag.TUPLE_TYPE, Flags.asMask(EnumSet.of(Flag.PUBLIC)),
                Names.EMPTY, symEnv.enclPkg.symbol.pkgID, null,
                symEnv.scope.owner, td.pos, BUILTIN);
        List<BTupleMember> memberTypes = new ArrayList<>();
        BTupleType tupleType = new BTupleType(tupleTypeSymbol, memberTypes);
        tupleTypeSymbol.type = tupleType;
        BTypeDefinition defn = new BTypeDefinition(tupleType);
//        BIntersectionType immutableType = new BIntersectionType(null, null, null);
//        defn.setImmutableType(immutableType);
        td.defn = defn;
        td.setBType(tupleType);
        unresolvedTypes.push(tupleType);

        for (BLangType memberTypeNode : td.getMemberTypeNodes()) {
            BType type = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, memberTypeNode);
            BVarSymbol varSymbol = Symbols.createVarSymbolForTupleMember(type);
            memberTypes.add(new BTupleMember(type, varSymbol));
        }

        if (td.restParamType != null) {
            BType tupleRestType = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.restParamType);
            if (tupleRestType.tag == TypeTags.SEMANTIC_ERROR) {
                return symTable.semanticError;
            }
            tupleType.restType = tupleRestType;
            symResolver.markParameterizedType(tupleType, tupleType.restType);
        }

        symResolver.markParameterizedType(tupleType, tupleType.getTupleTypes());
        unresolvedTypes.pop();
        return tupleType;
    }

    private BType resolveTypeDesc(BLangRecordTypeNode td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;

        if (td.defn != null) {
            return td.defn.getMutableType();
        }

        EnumSet<Flag> flags = td.isAnonymous ? EnumSet.of(Flag.PUBLIC, Flag.ANONYMOUS)
                : EnumSet.noneOf(Flag.class);
        BRecordTypeSymbol recordSymbol = Symbols.createRecordSymbol(Flags.asMask(flags), Names.EMPTY,
                symEnv.enclPkg.symbol.pkgID, null,
                symEnv.scope.owner, td.pos,
                td.isAnonymous ? VIRTUAL : BUILTIN);
        BRecordType recordType = new BRecordType(recordSymbol);
        resolvingStructureTypes.add(recordType);
        recordSymbol.type = recordType;
        td.symbol = recordSymbol;
        BTypeDefinition defn = new BTypeDefinition(recordType);
        td.defn = defn;
        td.setBType(recordType);
        unresolvedTypes.push(recordType);

        if (symEnv.node.getKind() != NodeKind.PACKAGE) {
            recordSymbol.name = names.fromString(
                    anonymousModelHelper.getNextAnonymousTypeKey(symEnv.enclPkg.packageID));
            symEnter.defineSymbol(td.pos, td.symbol, symEnv);
            symEnter.defineNode(td, symEnv);
        }

        if (td.getRestFieldType() != null) {
            resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.restFieldType);
            currentDepth = depth;
        }

        boolean errored = false;
        for (BLangType bLangRefType : td.typeRefs) {
            BType refType = resolveTypeDesc(symEnv, mod, typeDefinition, depth, bLangRefType);
            currentDepth = depth;
            if (refType == symTable.semanticError) {
                errored = true;
            }
        }

        defineTypeDefinition(typeDefinition, recordType, symEnv); // swj: define symbols, set flags ...
        symEnter.populateDistinctTypeIdsFromIncludedTypeReferences(typeDefinition);
        currentDepth = depth;
        if (errored) {
            typeDefinition.referencedFieldsDefined = true;
            defineRestFields(typeDefinition);
        }
        defineFieldsOftypeDefinition(typeDefinition, symEnv, mod);
        resolvingStructureTypes.remove(recordType);
        unresolvedTypes.pop();
        return recordType;
    }

    private BType resolveTypeDesc(BLangObjectTypeNode td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;

        if (td.defn != null) {
            return td.defn.getMutableType();
        }
        EnumSet<Flag> flags = EnumSet.copyOf(td.flagSet);
        if (td.isAnonymous) {
            flags.add(Flag.PUBLIC);
        }

        int typeFlags = 0;
        if (flags.contains(Flag.READONLY)) {
            typeFlags |= Flags.READONLY;
        }

        if (flags.contains(Flag.ISOLATED)) {
            typeFlags |= Flags.ISOLATED;
        }

        if (flags.contains(Flag.SERVICE)) {
            typeFlags |= Flags.SERVICE;
        }

        BTypeSymbol objectSymbol = Symbols.createObjectSymbol(Flags.asMask(flags), Names.EMPTY,
                symEnv.enclPkg.symbol.pkgID, null, symEnv.scope.owner, td.pos, BUILTIN);

        BObjectType objectType = new BObjectType(objectSymbol, typeFlags);
        resolvingStructureTypes.add(objectType);
        objectSymbol.type = objectType;
        td.symbol = objectSymbol;
        td.setBType(objectType);
        td.defn = new BTypeDefinition(objectType);
        unresolvedTypes.push(objectType);

        boolean errored = false;
        for (BLangType bLangRefType : td.typeRefs) {
            BType refType = resolveTypeDesc(symEnv, mod, typeDefinition, depth, bLangRefType);
            if (refType == symTable.semanticError) {
                errored = true;
            }
        }

        if (errored) {
            typeDefinition.referencedFieldsDefined = true;
        }

        symResolver.validateDistinctType(td, objectType);

        defineTypeDefinition(typeDefinition, objectType, symEnv); // swj: define symbols, set flags ...
        symEnter.defineDistinctClassAndObjectDefinitions(new ArrayList<>(Arrays.asList(typeDefinition)));
        symEnter.populateDistinctTypeIdsFromIncludedTypeReferences(typeDefinition);
        defineFieldsOftypeDefinition(typeDefinition, symEnv, mod);
        resolvingStructureTypes.remove(objectType);
        unresolvedTypes.pop();
        return objectType;
    }

    private void defineFieldsOftypeDefinition(BLangNode typeDefOrObject, SymbolEnv symEnv, Map<String, BLangNode> mod) {
        // Temporarily
        List<BLangNode> typeAndClassDefs = new ArrayList<>();
        typeAndClassDefs.add(typeDefOrObject);
//        symEnter.defineFields(typeAndClassDefs, symEnv);
        symEnter.populateTypeToTypeDefMap(typeAndClassDefs);
//        symEnter.defineDependentFields(typeAndClassDefs, symEnv);
        currentDepth++;
        defineField(typeDefOrObject, mod, symEnv);
    }

    private BType resolveTypeDesc(BLangFunctionTypeNode td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;
        if (td.defn != null) {
            return td.defn.getMutableType();
        }

        List<BLangSimpleVariable> params = td.getParams();
        Location pos = td.pos;
        BLangType returnTypeNode = td.returnTypeNode;
        BType invokableType = createInvokableType(params, td.restParam, returnTypeNode,
                Flags.asMask(td.flagSet), pos, symEnv, mod, depth, typeDefinition);
        return symResolver.validateInferTypedescParams(pos, params, returnTypeNode == null ?
                null : returnTypeNode.getBType()) ? invokableType : symTable.semanticError;
    }

    public BType createInvokableType(List<? extends BLangVariable> paramVars,
                                     BLangVariable restVariable,
                                     BLangType retTypeVar,
                                     long flags,
                                     Location location, SymbolEnv env, Map<String, BLangNode> mod, int depth,
                                     BLangTypeDefinition typeDefinition) {
        List<BType> paramTypes = new ArrayList<>();
        List<BVarSymbol> params = new ArrayList<>();

        boolean foundDefaultableParam = false;
        List<String> paramNames = new ArrayList<>();
        if (Symbols.isFlagOn(flags, Flags.ANY_FUNCTION)) {
            BInvokableType bInvokableType = new BInvokableType(null, null, null, null);
            bInvokableType.flags = flags;
            BInvokableTypeSymbol tsymbol = Symbols.createInvokableTypeSymbol(SymTag.FUNCTION_TYPE, flags,
                    env.enclPkg.symbol.pkgID, bInvokableType,
                    env.scope.owner, location, BUILTIN);
            tsymbol.params = null;
            tsymbol.restParam = null;
            tsymbol.returnType = null;
            bInvokableType.tsymbol = tsymbol;
            return bInvokableType;
        }

        for (BLangVariable paramNode : paramVars) {
            BLangSimpleVariable param = (BLangSimpleVariable) paramNode;
            Name paramName = names.fromIdNode(param.name);
            Name paramOrigName = names.originalNameFromIdNode(param.name);
            if (paramName != Names.EMPTY) {
                if (paramNames.contains(paramName.value)) {
                    dlog.error(param.name.pos, DiagnosticErrorCode.REDECLARED_SYMBOL, paramName.value);
                } else {
                    paramNames.add(paramName.value);
                }
            }
            BType type = resolveTypeDesc(env, mod, typeDefinition, depth + 1, param.getTypeNode());
            paramNode.setBType(type);
            paramTypes.add(type);

            long paramFlags = Flags.asMask(paramNode.flagSet);
            BVarSymbol symbol = new BVarSymbol(paramFlags, paramName, paramOrigName, env.enclPkg.symbol.pkgID,
                    type, env.scope.owner, param.pos, BUILTIN);
            param.symbol = symbol;

            if (param.expr != null) {
                foundDefaultableParam = true;
                symbol.isDefaultable = true;
                symbol.flags |= Flags.OPTIONAL;
            } else if (foundDefaultableParam) {
                dlog.error(param.pos, DiagnosticErrorCode.REQUIRED_PARAM_DEFINED_AFTER_DEFAULTABLE_PARAM);
            }

            params.add(symbol);
        }

        BType retType = resolveTypeDesc(env, mod, typeDefinition, depth + 1, retTypeVar);

        BVarSymbol restParam = null;
        BType restType = null;

        if (restVariable != null) {
            restType = resolveTypeDesc(env, mod, typeDefinition, depth + 1, restVariable.typeNode);
            BLangIdentifier id = ((BLangSimpleVariable) restVariable).name;
            restVariable.setBType(restType);
            restParam = new BVarSymbol(Flags.asMask(restVariable.flagSet),
                    names.fromIdNode(id), names.originalNameFromIdNode(id),
                    env.enclPkg.symbol.pkgID, restType, env.scope.owner, restVariable.pos, BUILTIN);
            restVariable.symbol = restParam;
        }

        BInvokableType bInvokableType = new BInvokableType(paramTypes, restType, retType, null);
        bInvokableType.flags |= flags;
        BInvokableTypeSymbol tsymbol = Symbols.createInvokableTypeSymbol(SymTag.FUNCTION_TYPE, flags,
                env.enclPkg.symbol.pkgID, bInvokableType,
                env.scope.owner, location, BUILTIN);
        tsymbol.name = names.fromString(anonymousModelHelper.getNextAnonymousTypeKey(env.enclPkg.packageID));
        symEnter.defineSymbol(location, tsymbol, env);
        tsymbol.params = params;
        tsymbol.restParam = restParam;
        tsymbol.returnType = retType;
        bInvokableType.tsymbol = tsymbol;
        params.forEach(param -> tsymbol.scope.define(param.name, param));

        List<BType> allConstituentTypes = new ArrayList<>(paramTypes);
        allConstituentTypes.add(restType);
        allConstituentTypes.add(retType);
        symResolver.markParameterizedType(bInvokableType, allConstituentTypes);

        return bInvokableType;
    }

    private BType resolveTypeDesc(BLangErrorType td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition defn) {
        currentDepth = depth;
        if (td.detailType == null) {
            BType errorType = new BErrorType(null, symTable.detailType);
            errorType.tsymbol = new BErrorTypeSymbol(SymTag.ERROR, Flags.PUBLIC, Names.ERROR,
                    symTable.rootPkgSymbol.pkgID, errorType, symTable.rootPkgSymbol, symTable.builtinPos, BUILTIN);
            return errorType;
        }

        SymbolResolver.AnalyzerData data = new SymbolResolver.AnalyzerData(symEnv);

        BType detailType = Optional.ofNullable(td.detailType)
                .map(bLangType -> resolveTypeDesc(symEnv, mod, defn, depth, bLangType)).orElse(symTable.detailType);

        if (td.isAnonymous) {
            td.flagSet.add(Flag.PUBLIC);
            td.flagSet.add(Flag.ANONYMOUS);
        }

        // The builtin error type
        BErrorType bErrorType = symTable.errorType;

        boolean distinctErrorDef = td.flagSet.contains(Flag.DISTINCT);
        if (detailType == symTable.detailType && !distinctErrorDef &&
                !data.env.enclPkg.packageID.equals(PackageID.ANNOTATIONS)) {
            return bErrorType;
        }

        // Define user define error type.
        BErrorTypeSymbol errorTypeSymbol = Symbols.createErrorSymbol(Flags.asMask(td.flagSet),
                Names.EMPTY, data.env.enclPkg.packageID, null, data.env.scope.owner, td.pos, BUILTIN);

        PackageID packageID = data.env.enclPkg.packageID;
        if (data.env.node.getKind() != NodeKind.PACKAGE) {
            errorTypeSymbol.name = names.fromString(
                    anonymousModelHelper.getNextAnonymousTypeKey(packageID));
            symEnter.defineSymbol(td.pos, errorTypeSymbol, data.env);
        }

        BErrorType errorType = new BErrorType(errorTypeSymbol, detailType);
        errorType.flags |= errorTypeSymbol.flags;
        errorTypeSymbol.type = errorType;

        symResolver.markParameterizedType(errorType, detailType);

        errorType.typeIdSet = BTypeIdSet.emptySet();

        if (td.isAnonymous && td.flagSet.contains(Flag.DISTINCT)) {
            errorType.typeIdSet.add(
                    BTypeIdSet.from(packageID, anonymousModelHelper.getNextAnonymousTypeId(packageID), true));
        }

        return errorType;
    }

    private BType resolveTypeDesc(BLangUnionTypeNode td, SymbolEnv symEnv,
                                  Map<String, BLangNode> mod, int depth, BLangTypeDefinition typeDefinition) {
        currentDepth = depth;

        if (td.defn != null) {
            return td.defn.getMutableType();
        }

        LinkedHashSet<BType> memberTypes = new LinkedHashSet<>();
        BTypeSymbol unionTypeSymbol = Symbols.createTypeSymbol(SymTag.UNION_TYPE, Flags.asMask(EnumSet.of(Flag.PUBLIC)),
                Names.EMPTY, symEnv.enclPkg.symbol.pkgID, null,
                symEnv.scope.owner, td.pos, BUILTIN); // ***swj
        BUnionType unionType = new BUnionType(unionTypeSymbol, memberTypes, false, false);
        unionTypeSymbol.type = unionType;
        BTypeDefinition defn = new BTypeDefinition(unionType);
        td.defn = defn;
        td.setBType(unionType);
        unresolvedTypes.push(unionType);

        boolean errored = false;
        for (BLangType langType : td.memberTypeNodes) {
            BType resolvedType = resolveTypeDesc(symEnv, mod, typeDefinition, depth, langType);
            if (resolvedType == symTable.semanticError) {
                errored = true;
                continue;
            }

            if (resolvedType.isNullable()) {
                unionType.setNullable(true);
            }
            memberTypes.add(resolvedType);
        }

        if (errored) {
            return symTable.semanticError;
        }

        updateReadOnlyAndNullableFlag(unionType);
        symResolver.markParameterizedType(unionType, memberTypes);
        unresolvedTypes.pop();
        return unionType;
    }

    private void updateReadOnlyAndNullableFlag(BUnionType type) {
        LinkedHashSet<BType> memberTypes = type.getMemberTypes();
        LinkedHashSet<BType> flattenMemberTypes = new LinkedHashSet<>(memberTypes.size());
        boolean isImmutable = true;
        boolean hasNilableType = false;

        for (BType memBType : BUnionType.toFlatTypeSet(memberTypes)) {
            if (Types.getReferredType(memBType).tag != TypeTags.NEVER) {
                flattenMemberTypes.add(memBType);
            }

            if (isImmutable && !Symbols.isFlagOn(memBType.flags, Flags.READONLY)) {
                isImmutable = false;
            }
        }

        if (isImmutable) {
            type.flags |= Flags.READONLY;
            if (type.tsymbol != null) {
                type.tsymbol.flags |= Flags.READONLY;
            }
        }

        for (BType memberType : flattenMemberTypes) {
            if (memberType.isNullable() && memberType.tag != TypeTags.NIL) {
                hasNilableType = true;
                break;
            }
        }

        if (hasNilableType) {
            LinkedHashSet<BType> bTypes = new LinkedHashSet<>(flattenMemberTypes.size());
            for (BType t : flattenMemberTypes) {
                if (t.tag != TypeTags.NIL) {
                    bTypes.add(t);
                }
            }
            flattenMemberTypes = bTypes;
        }

        for (BType memberType : flattenMemberTypes) {
            if (memberType.isNullable()) {
                type.setNullable(true);
            }
        }
        memberTypes.clear();
        memberTypes.addAll(flattenMemberTypes);
    }

    private BType resolveTypeDesc(BLangIntersectionTypeNode td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;
        List<BLangType> constituentTypeNodes = td.constituentTypeNodes;

        BTypeSymbol intersectionSymbol = Symbols.createTypeSymbol(SymTag.ARRAY_TYPE, Flags.PUBLIC, Names.EMPTY,
                symEnv.enclPkg.symbol.pkgID, null, symEnv.scope.owner, td.pos, BUILTIN);
        BIntersectionType intersectionType = new BIntersectionType(intersectionSymbol);
        intersectionSymbol.type = intersectionType;
        BTypeDefinition defn = new BTypeDefinition(intersectionType);
        td.defn = defn;
        LinkedHashSet<BType> constituentTypes = new LinkedHashSet<>();
        unresolvedTypes.push(intersectionType);

        boolean errored = false;
        Set<BType> errorTypes = new HashSet<>();
        for (BLangType typeNode : constituentTypeNodes) {
            BType constituentType = resolveTypeDesc(symEnv, mod, typeDefinition, depth, typeNode);
            if (constituentType == symTable.semanticError) {
                errored = true;
                continue;
            }
            if (constituentType.tag == TypeTags.READONLY) {
                intersectionType.flags |= Flags.READONLY;
            }
            constituentTypes.add(constituentType);
            if (Types.getReferredType(constituentType).tag == TypeTags.ERROR) {
                errorTypes.add(constituentType);
            }
        }
        if (errored) {
            return symTable.semanticError;
        }
        intersectionType.setConstituentTypes(constituentTypes);

        boolean isErrorIntersection = errorTypes.size() > 1;
        if (isErrorIntersection) {
            long flags = 0;
            if (typeDefinition.flagSet.contains(Flag.PUBLIC)) {
                flags = Flags.PUBLIC;
            }

            BErrorType intersectionErrorType = types.createErrorType(null, flags, symEnv);
            intersectionErrorType.tsymbol.name = names.fromString(typeDefinition.name.value);
            defineErrorType(typeDefinition.pos, intersectionErrorType, symEnv);
        }
        unresolvedTypes.pop();
        fillEffectiveType(typeDefinition.name, intersectionType, td, symEnv);
        BType effectiveType = intersectionType.effectiveType;
        if (!isErrorIntersection && types.isInherentlyImmutableType(effectiveType)) {
            return effectiveType;
        }
        return intersectionType;
    }

    private void fillEffectiveType(BLangIdentifier name, BIntersectionType intersectionType,
                                   BLangIntersectionTypeNode td, SymbolEnv env) {
        List<BLangType> constituentTypeNodes = td.constituentTypeNodes;
        Iterator<BLangType> bLangTypeItr = constituentTypeNodes.iterator();
        Iterator<BType> iterator = intersectionType.getConstituentTypes().iterator();
        BType effectiveType = iterator.next();
        BLangType bLangEffectiveType = bLangTypeItr.next();
        if (effectiveType.tag == TypeTags.READONLY && iterator.hasNext()) {
            intersectionType.flags = intersectionType.flags | TypeTags.READONLY;
            effectiveType = iterator.next();
            bLangEffectiveType = bLangTypeItr.next();
        }

        while (iterator.hasNext()) {
            BType type = iterator.next();
            BLangType bLangType = bLangTypeItr.next();
            if (type.tag == TypeTags.READONLY) {
                intersectionType.flags = intersectionType.flags | TypeTags.READONLY;
                continue;
            }

            effectiveType = calculateEffectiveType(name, td, bLangEffectiveType, bLangType, effectiveType, type);
            if (effectiveType.tag == TypeTags.SEMANTIC_ERROR) {
                intersectionType.effectiveType = symTable.semanticError;
                return;
            }
        }
        intersectionType.effectiveType = effectiveType;

        if ((intersectionType.flags & Flags.READONLY) == Flags.READONLY &&
                !types.isInherentlyImmutableType(intersectionType.effectiveType)) {
            if (!unresolvedTypes.isEmpty()) {
                intersectionTypeList.put(intersectionType, td);
            }
            BIntersectionType immutableIntersectionType =
                    ImmutableTypeCloner.getImmutableIntersectionType(intersectionType.tsymbol.pos, types,
                            intersectionType.effectiveType, env, symTable, anonymousModelHelper, names,
                            new HashSet<>());
            intersectionType.effectiveType = immutableIntersectionType.effectiveType;
        }
    }

    private BType resolveTypeDesc(BLangUserDefinedType td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth) {
        currentDepth = depth;
        String name = td.typeName.value;

        BType type;

        // 1) Resolve the package scope using the package alias.
        //    If the package alias is not empty or null, then find the package scope,
        //    if not use the current package scope.
        // 2) lookup the typename in the package scope returned from step 1.
        // 3) If the symbol is not found, then lookup in the root scope. e.g. for types such as 'error'
        SymbolResolver.AnalyzerData data = new SymbolResolver.AnalyzerData(symEnv);

        Name pkgAlias = names.fromIdNode(td.pkgAlias);
        Name typeName = names.fromIdNode(td.typeName);
        BSymbol symbol = symTable.notFoundSymbol;

        // 1) Resolve ANNOTATION type if and only current scope inside ANNOTATION definition.
        // Only valued types and ANNOTATION type allowed.
        if (symEnv.scope.owner.tag == SymTag.ANNOTATION) {
            symbol = symResolver.lookupAnnotationSpaceSymbolInPackage(td.pos, symEnv, pkgAlias, typeName);
        }

        // 2) Resolve the package scope using the package alias.
        //    If the package alias is not empty or null, then find the package scope,
        if (symbol == symTable.notFoundSymbol) {
            BSymbol tempSymbol = symResolver.lookupMainSpaceSymbolInPackage(td.pos, symEnv, pkgAlias, typeName);
            BSymbol refSymbol = tempSymbol.tag == SymTag.TYPE_DEF ? Types.getReferredType(tempSymbol.type).tsymbol
                    : tempSymbol;
            if ((refSymbol.tag & SymTag.TYPE) == SymTag.TYPE) {
                symbol = tempSymbol;
            } else if (Symbols.isTagOn(refSymbol, SymTag.VARIABLE) && symEnv.node.getKind() == NodeKind.FUNCTION) {
                BLangFunction func = (BLangFunction) symEnv.node;
                boolean errored = false;

                if (func.returnTypeNode == null ||
                        (func.hasBody() && func.body.getKind() != NodeKind.EXTERN_FUNCTION_BODY)) {
                    dlog.error(td.pos,
                            DiagnosticErrorCode.INVALID_NON_EXTERNAL_DEPENDENTLY_TYPED_FUNCTION);
                    errored = true;
                }

                if (tempSymbol.type != null &&
                        Types.getReferredType(tempSymbol.type).tag != TypeTags.TYPEDESC) {
                    dlog.error(td.pos, DiagnosticErrorCode.INVALID_PARAM_TYPE_FOR_RETURN_TYPE,
                            tempSymbol.type);
                    errored = true;
                }

                if (errored) {
                    return symTable.semanticError;
                }

                SymbolResolver.ParameterizedTypeInfo parameterizedTypeInfo =
                        symResolver.getTypedescParamValueType(func.requiredParams, data, refSymbol);
                BType paramValType = parameterizedTypeInfo == null ? null : parameterizedTypeInfo.paramValueType;

                if (paramValType == symTable.semanticError) {
                    return symTable.semanticError;
                }

                if (paramValType != null) {
                    BTypeSymbol tSymbol = new BTypeSymbol(SymTag.TYPE, Flags.PARAMETERIZED | tempSymbol.flags,
                            tempSymbol.name, tempSymbol.originalName, tempSymbol.pkgID,
                            null, func.symbol, tempSymbol.pos, VIRTUAL);
                    tSymbol.type = new BParameterizedType(paramValType, (BVarSymbol) tempSymbol,
                            tSymbol, tempSymbol.name, parameterizedTypeInfo.index);
                    tSymbol.type.flags |= Flags.PARAMETERIZED;

                    td.symbol = tSymbol;
                    return tSymbol.type;
                }
            }
        }

        if (symbol == symTable.notFoundSymbol) {
            // 3) Lookup the root scope for types such as 'error'
            symbol = symResolver.lookupMemberSymbol(td.pos, symTable.rootScope, symEnv, typeName,
                    SymTag.VARIABLE_NAME);
        }

        if (symEnv.logErrors && symbol == symTable.notFoundSymbol) {
            if (!missingNodesHelper.isMissingNode(pkgAlias) && !missingNodesHelper.isMissingNode(typeName) &&
                    !symEnter.isUnknownTypeRef(td)) {
                dlog.error(td.pos, data.diagCode, typeName);
            }
            return symTable.semanticError;
        }

        td.symbol = symbol;

        if (symbol.kind == SymbolKind.TYPE_DEF && !Symbols.isFlagOn(symbol.flags, Flags.ANONYMOUS)) {
            BType referenceType = ((BTypeDefinitionSymbol) symbol).referenceType;
            referenceType.flags |= symbol.type.flags;
            referenceType.tsymbol.flags |= symbol.type.flags;
            return referenceType;
        }

        type = symbol.type;

        if (symbol != symTable.notFoundSymbol) {
            return type;
        }

        BLangNode moduleLevelDef = mod.get(name);
        if (moduleLevelDef == null) {
            if (missingNodesHelper.isMissingNode(pkgAlias) || missingNodesHelper.isMissingNode(typeName)) {
                return symTable.semanticError;
            }

            LocationData locationData = new LocationData(name, td.pos.lineRange().startLine().line(),
                    td.pos.lineRange().startLine().offset());
            if (unknownTypeRefs.add(locationData)) {
                dlog.error(td.pos, DiagnosticErrorCode.UNKNOWN_TYPE, td.typeName);
            }
            return symTable.semanticError;
        }

        if (moduleLevelDef.getKind() == NodeKind.TYPE_DEFINITION) {
            BLangTypeDefinition typeDefinition = (BLangTypeDefinition) moduleLevelDef;
            BType resolvedType = resolveTypeDefinition(symEnv, mod, typeDefinition, depth);
//            symEnter.populateDistinctTypeIdsFromIncludedTypeReferences(typeDefinition); // temporary disable due to intersections
            if (resolvedType == symTable.semanticError || resolvedType == symTable.noType) {
                return resolvedType;
            }
            symbol = typeDefinition.symbol;
            td.symbol = symbol;
            if (symbol.kind == SymbolKind.TYPE_DEF && !Symbols.isFlagOn(symbol.flags, Flags.ANONYMOUS)) {
                BType referenceType = ((BTypeDefinitionSymbol) symbol).referenceType;
                referenceType.flags |= symbol.type.flags;
                referenceType.tsymbol.flags |= symbol.type.flags;
                return referenceType;
            }
            return resolvedType;
        } else if (moduleLevelDef.getKind() == NodeKind.CONSTANT) {
            BLangConstant constant = (BLangConstant) moduleLevelDef;
            resolveConstant(symEnv, mod, constant);
            return constant.getBType();
        } else {
            return extracted(symEnv, mod, (BLangClassDefinition) moduleLevelDef);
        }
    }

    private BType resolveTypeDesc(BLangBuiltInRefTypeNode td, SymbolEnv symEnv) {
        SymbolResolver.AnalyzerData data = new SymbolResolver.AnalyzerData(symEnv);
        return visitBuiltInTypeNode(td, data, td.typeKind);
    }

    private BType resolveSingletonType(BLangFiniteTypeNode td, SymbolEnv symEnv) {
        BTypeSymbol finiteTypeSymbol = Symbols.createTypeSymbol(SymTag.FINITE_TYPE,
                Flags.asMask(EnumSet.noneOf(Flag.class)), Names.EMPTY, symEnv.enclPkg.symbol.pkgID, null,
                symEnv.scope.owner, td.pos, BUILTIN);

        // In case we encounter unary expressions in finite type, we will be replacing them with numeric literals.
         replaceUnaryExprWithNumericLiteral(td);

        BFiniteType finiteType = new BFiniteType(finiteTypeSymbol);
        for (BLangExpression literal : td.valueSpace) {
            BType type = blangTypeUpdate(literal);
            if (type != null && type.tag == TypeTags.SEMANTIC_ERROR) {
                return type;
            }
            if (type != null) {
                literal.setBType(symTable.getTypeFromTag(type.tag));
            }
            finiteType.addValue(literal);
        }
        finiteTypeSymbol.type = finiteType;
        td.setBType(finiteType);
        return finiteType;
    }

    private void replaceUnaryExprWithNumericLiteral(BLangFiniteTypeNode finiteTypeNode) {
        BLangExpression value;
        NodeKind valueKind;
        for(int i = 0; i < finiteTypeNode.valueSpace.size(); i++) {
            value = finiteTypeNode.valueSpace.get(i);
            valueKind = value.getKind();

            if (valueKind == NodeKind.UNARY_EXPR) {
                BLangUnaryExpr unaryExpr = (BLangUnaryExpr) value;
                if (unaryExpr.expr.getKind() == NodeKind.NUMERIC_LITERAL) {
                    // Replacing unary expression with numeric literal type for + and - numeric values
                    BLangNumericLiteral newNumericLiteral =
                            Types.constructNumericLiteralFromUnaryExpr(unaryExpr);
                    finiteTypeNode.valueSpace.set(i, newNumericLiteral);
                }
            }
        }
    }

    private BType blangTypeUpdate(BLangExpression expression) {
        BType type;
        switch (expression.getKind()) {
            case UNARY_EXPR:
                type = blangTypeUpdate(((BLangUnaryExpr) expression).expr);
                expression.setBType(type);
                return type;
            case GROUP_EXPR:
                type = blangTypeUpdate(((BLangGroupExpr) expression).expression);
                expression.setBType(type);
                return type;
            case LITERAL:
                return ((BLangLiteral) expression).getBType();
            case BINARY_EXPR:
                type = blangTypeUpdate(((BLangBinaryExpr) expression).lhsExpr);
                expression.setBType(type);
                return type;
            case NUMERIC_LITERAL:
                BLangNumericLiteral expr = (BLangNumericLiteral) expression;
                if (expr.getBType().tag == TypeTags.INT && !(expr.value instanceof Long)) {
                    dlog.error(expression.pos, DiagnosticErrorCode.OUT_OF_RANGE, expr.originalValue,
                            expression.getBType());
                    return symTable.semanticError;
                }
                return expr.getBType();
            default:
                return null;
        }
    }

    private BType resolveTypeDesc(BLangTableTypeNode td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth, BLangTypeDefinition typeDefinition) {
//        SemType memberType =
//                resolveTypeDesc(semtypeEnv, mod, (BLangTypeDefinition) td.constraint.defn, depth, td.constraint);
//        return SemTypes.tableContaining(memberType);
        currentDepth = depth;
        BType type = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.type);//resolveTypeNode(td.type, data, data.env);
        BType constraintType =
                resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.constraint);// resolveTypeNode(td.constraint, data, data.env);

        BTableType tableType = new BTableType(TypeTags.TABLE, constraintType, null);
        BTypeSymbol typeSymbol = type.tsymbol;
        tableType.tsymbol = Symbols.createTypeSymbol(SymTag.TYPE, Flags.asMask(EnumSet.noneOf(Flag.class)),
                typeSymbol.name, typeSymbol.originalName, typeSymbol.pkgID,
                tableType, symEnv.scope.owner, td.pos, BUILTIN);
        tableType.tsymbol.flags = typeSymbol.flags;
        tableType.constraintPos = td.constraint.pos;
        tableType.isTypeInlineDefined = td.isTypeInlineDefined;

        if (td.tableKeyTypeConstraint != null) {
            tableType.keyTypeConstraint = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.tableKeyTypeConstraint.keyType);//resolveTypeNode(td.tableKeyTypeConstraint.keyType, data, symEnv);
            tableType.keyPos = td.tableKeyTypeConstraint.pos;
        } else if (td.tableKeySpecifier != null) {
            BLangTableKeySpecifier tableKeySpecifier = td.tableKeySpecifier;
            List<String> fieldNameList = new ArrayList<>();
            for (IdentifierNode identifier : tableKeySpecifier.fieldNameIdentifierList) {
                fieldNameList.add(((BLangIdentifier) identifier).value);
            }
            tableType.fieldNameList = fieldNameList;
            tableType.keyPos = tableKeySpecifier.pos;
        }

        if (Types.getReferredType(constraintType).tag == TypeTags.MAP &&
                (!tableType.fieldNameList.isEmpty() || tableType.keyTypeConstraint != null) &&
                !tableType.tsymbol.owner.getFlags().contains(Flag.LANG_LIB)) {
            dlog.error(tableType.keyPos,
                    DiagnosticErrorCode.KEY_CONSTRAINT_NOT_SUPPORTED_FOR_TABLE_WITH_MAP_CONSTRAINT);
            return symTable.semanticError;
        }

        symResolver.markParameterizedType(tableType, constraintType);
        td.tableType = tableType;

        return tableType;
    }

    private BType resolveTypeDesc(BLangStreamType td, SymbolEnv symEnv, Map<String, BLangNode> mod, int depth,
                                  BLangTypeDefinition typeDefinition) {
        currentDepth = depth;
        BType type = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.type);
        BType constraintType = resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.constraint);
        BType error = td.error != null ?
                resolveTypeDesc(symEnv, mod, typeDefinition, depth + 1, td.error) : symTable.nilType;

        BType streamType = new BStreamType(TypeTags.STREAM, constraintType, error, null);
        BTypeSymbol typeSymbol = type.tsymbol;
        streamType.tsymbol = Symbols.createTypeSymbol(typeSymbol.tag, typeSymbol.flags, typeSymbol.name,
                typeSymbol.originalName, typeSymbol.pkgID, streamType,
                symEnv.scope.owner, td.pos, BUILTIN);

        symResolver.markParameterizedType(streamType, constraintType);
        if (error != null) {
            symResolver.markParameterizedType(streamType, error);
        }

        return streamType;
    }

    public BType visitBuiltInTypeNode(BLangType typeNode, SymbolResolver.AnalyzerData data, TypeKind typeKind) {
        Name typeName = names.fromTypeKind(typeKind);
        BSymbol typeSymbol = symResolver.lookupMemberSymbol(typeNode.pos, symTable.rootScope, data.env, typeName, SymTag.TYPE);
        if (typeSymbol == symTable.notFoundSymbol) {
            dlog.error(typeNode.pos, data.diagCode, typeName);
        }

        typeNode.setBType(typeSymbol.type);
        return typeSymbol.type;
    }

    private int constExprToInt(BLangExpression t) {
        if (t.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
            BConstantSymbol symbol = (BConstantSymbol) ((BLangSimpleVarRef) t).symbol;
            return ((Long) symbol.value.value).intValue();
        }
        return (int) ((BLangLiteral) t).value;
    }

    public BType defineTypeDefinition(BLangTypeDefinition typeDefinition, BType resolvedType, SymbolEnv env) {

        if (resolvedType == null || resolvedType == symTable.noType || typeDefinition.symbol != null) {
            return resolvedType;
        }

        // Check for any circular type references
        NodeKind typeNodeKind = typeDefinition.typeNode.getKind();
        if (typeNodeKind == NodeKind.OBJECT_TYPE || typeNodeKind == NodeKind.RECORD_TYPE) {
            if (resolvedType.tsymbol.scope == null) {
                resolvedType.tsymbol.scope = new Scope(resolvedType.tsymbol);
            }
        }

        if (typeDefinition.flagSet.contains(Flag.ENUM)) {
            resolvedType.tsymbol = createEnumSymbol(typeDefinition, resolvedType, env);
        }

        typeDefinition.setPrecedence(this.typePrecedence++);
        BSymbol typeDefSymbol = Symbols.createTypeDefinitionSymbol(Flags.asMask(typeDefinition.flagSet),
                names.fromIdNode(typeDefinition.name), env.enclPkg.packageID, resolvedType, env.scope.owner,
                typeDefinition.name.pos, symEnter.getOrigin(typeDefinition.name.value));
        typeDefSymbol.markdownDocumentation = symEnter.getMarkdownDocAttachment(typeDefinition.markdownDocumentationAttachment);
        BTypeSymbol typeSymbol = new BTypeSymbol(SymTag.TYPE_REF, typeDefSymbol.flags, typeDefSymbol.name,
                typeDefSymbol.pkgID, typeDefSymbol.type, typeDefSymbol.owner, typeDefSymbol.pos, typeDefSymbol.origin);
        typeSymbol.markdownDocumentation = typeDefSymbol.markdownDocumentation;
        ((BTypeDefinitionSymbol) typeDefSymbol).referenceType = new BTypeReferenceType(resolvedType, typeSymbol,
                typeDefSymbol.type.flags);

        if (resolvedType == symTable.semanticError && resolvedType.tsymbol == null) {
            typeDefinition.symbol = typeDefSymbol;
            return resolvedType;
        }

        boolean isLabel = true;
        //todo remove after type ref introduced to runtime
        if (resolvedType.tsymbol.name == Names.EMPTY) {
            isLabel = false;
            resolvedType.tsymbol.name = names.fromIdNode(typeDefinition.name);
            resolvedType.tsymbol.originalName = names.originalNameFromIdNode(typeDefinition.name);
            resolvedType.tsymbol.flags |= typeDefSymbol.flags;

            resolvedType.tsymbol.markdownDocumentation = typeDefSymbol.markdownDocumentation;
            resolvedType.tsymbol.pkgID = env.enclPkg.packageID;
            if (resolvedType.tsymbol.tag == SymTag.ERROR) {
                resolvedType.tsymbol.owner = env.scope.owner;
            }
        }

        if ((((resolvedType.tsymbol.kind == SymbolKind.OBJECT
                && !Symbols.isFlagOn(resolvedType.tsymbol.flags, Flags.CLASS))
                || resolvedType.tsymbol.kind == SymbolKind.RECORD))
                && ((BStructureTypeSymbol) resolvedType.tsymbol).typeDefinitionSymbol == null) {
            ((BStructureTypeSymbol) resolvedType.tsymbol).typeDefinitionSymbol = (BTypeDefinitionSymbol) typeDefSymbol;
        }

        if (typeDefinition.flagSet.contains(Flag.ENUM)) {
            typeDefSymbol = resolvedType.tsymbol;
            typeDefSymbol.pos = typeDefinition.name.pos;
        }

        BType referenceConstraintType = Types.getReferredType(resolvedType);
        boolean isIntersectionType = referenceConstraintType.tag == TypeTags.INTERSECTION && !isLabel;

        BType effectiveDefinedType = isIntersectionType ? ((BIntersectionType) referenceConstraintType).effectiveType :
                referenceConstraintType;

        boolean isErrorIntersection = isErrorIntersection(resolvedType);
        if (isErrorIntersection && effectiveDefinedType.tag == TypeTags.ERROR) {
            symEnter.populateSymbolNameOfErrorIntersection(resolvedType, typeDefinition.name.value);
//            symEnter.populateAllReadyDefinedErrorIntersection(resolvedType, typeDefinition, env);
        }

        boolean isIntersectionTypeWithNonNullEffectiveTypeSymbol =
                isIntersectionType && effectiveDefinedType != null && effectiveDefinedType.tsymbol != null;

        if (isIntersectionTypeWithNonNullEffectiveTypeSymbol && !types.isInherentlyImmutableType(effectiveDefinedType)) {
            BTypeSymbol effectiveTypeSymbol = effectiveDefinedType.tsymbol;
            effectiveTypeSymbol.name = typeDefSymbol.name;
            effectiveTypeSymbol.pkgID = typeDefSymbol.pkgID;
        }

        symEnter.handleDistinctDefinition(typeDefinition, typeDefSymbol, resolvedType, referenceConstraintType);
        resolvedType = typeDefSymbol.type; // update the distinct type

        typeDefSymbol.flags |= Flags.asMask(typeDefinition.flagSet);
        // Reset public flag when set on a non public type.
        typeDefSymbol.flags &= symEnter.getPublicFlagResetingMask(typeDefinition.flagSet, typeDefinition.typeNode);
        if (symEnter.isDeprecated(typeDefinition.annAttachments)) {
            typeDefSymbol.flags |= Flags.DEPRECATED;
        }

        // Reset origin for anonymous types
        if (Symbols.isFlagOn(typeDefSymbol.flags, Flags.ANONYMOUS)) {
            typeDefSymbol.origin = VIRTUAL;
        }

        if (typeDefinition.annAttachments.stream()
                .anyMatch(attachment -> attachment.annotationName.value.equals(Names.ANNOTATION_TYPE_PARAM.value))) {
            // TODO : Clean this. Not a nice way to handle this.
            //  TypeParam is built-in annotation, and limited only within lang.* modules.
            if (PackageID.isLangLibPackageID(env.enclPkg.packageID)) {
                typeDefSymbol.type = typeParamAnalyzer.createTypeParam(typeDefSymbol);
                typeDefSymbol.flags |= Flags.TYPE_PARAM;
                resolvedType = typeDefSymbol.type;
            } else {
                dlog.error(typeDefinition.pos, DiagnosticErrorCode.TYPE_PARAM_OUTSIDE_LANG_MODULE);
            }
        }
        resolvedType.flags |= typeDefSymbol.flags;

        if (isIntersectionTypeWithNonNullEffectiveTypeSymbol) {
            BTypeSymbol effectiveTypeSymbol = effectiveDefinedType.tsymbol;
            effectiveTypeSymbol.flags |= resolvedType.tsymbol.flags;
            effectiveTypeSymbol.origin = VIRTUAL;
            effectiveDefinedType.flags |= resolvedType.flags;
        }

        typeDefinition.symbol = typeDefSymbol;

//        if (typeDefinition.hasCyclicReference) {
            // Workaround for https://github.com/ballerina-platform/ballerina-lang/issues/29742
//            typeDefinition.getBType().tsymbol = resolvedType.tsymbol;
//        } else {
        boolean isLanglibModule = PackageID.isLangLibPackageID(env.enclPkg.packageID);
        if (isLanglibModule) {
            symEnter.handleLangLibTypes(typeDefinition, env);
            return resolvedType;
        }

        if (!isErrorIntersection || symEnter.lookupTypeSymbol(env, typeDefinition.name) == symTable.notFoundSymbol) {
            symEnter.defineSymbol(typeDefinition.name.pos, typeDefSymbol, env);
        }
        return resolvedType;
    }

    private boolean isErrorIntersection(BType definedType) {
        BType type = Types.getReferredType(definedType);
        if (type.tag == TypeTags.INTERSECTION) {
            if (((BIntersectionType) type).effectiveType != null
                    && ((BIntersectionType) type).effectiveType != symTable.semanticError) {
                BIntersectionType intersectionType = (BIntersectionType) type;
                return intersectionType.effectiveType.tag == TypeTags.ERROR;
            } else {
                return ((BIntersectionType) type).getConstituentTypes().stream()
                        .anyMatch(t -> Types.getReferredType(t).tag == TypeTags.ERROR);
            }
        }
        return false;
    }

    private BEnumSymbol createEnumSymbol(BLangTypeDefinition typeDefinition, BType definedType, SymbolEnv env) {
        List<BConstantSymbol> enumMembers = new ArrayList<>();

        List<BLangType> members = ((BLangUnionTypeNode) typeDefinition.typeNode).memberTypeNodes;
        for (BLangType member : members) {
            enumMembers.add((BConstantSymbol) ((BLangUserDefinedType) member).symbol);
        }

        BEnumSymbol enumSymbol = new BEnumSymbol(enumMembers, Flags.asMask(typeDefinition.flagSet),
                names.fromIdNode(typeDefinition.name), names.fromIdNode(typeDefinition.name),
                env.enclPkg.symbol.pkgID, definedType, env.scope.owner,
                typeDefinition.pos, SOURCE);

        enumSymbol.name = names.fromIdNode(typeDefinition.name);
        enumSymbol.originalName = names.fromIdNode(typeDefinition.name);
        enumSymbol.flags |= Flags.asMask(typeDefinition.flagSet);

        enumSymbol.markdownDocumentation =
                symEnter.getMarkdownDocAttachment(typeDefinition.markdownDocumentationAttachment);
        enumSymbol.pkgID = env.enclPkg.packageID;
        return enumSymbol;
    }

    public void resolveConstant(SymbolEnv symEnv, Map<String, BLangNode> modTable, BLangConstant constant) {
        if (!resolvingConstants.add(constant)) { // To identify cycles.
            dlog.error(constant.pos, DiagnosticErrorCode.CONSTANT_CYCLIC_REFERENCE,
                    (this.resolvingConstants).stream().map(constNode -> constNode.symbol)
                            .collect(Collectors.toList()));
            return;
        }
        if (resolvedConstants.contains(constant)) { // Already resolved constant.
            resolvingConstants.remove(constant);
            return;
        }
        constantTypeChecker.anonTypeNameSuffixes.push(constant.name.value);
        defineConstant(symEnv, modTable, constant);
        constantTypeChecker.anonTypeNameSuffixes.pop();
        resolvingConstants.remove(constant);
        resolvedConstants.add(constant);
        checkUniqueness(constant);
    }

    private void checkUniqueness(BLangConstant constant) {
        if (constant.symbol.kind == SymbolKind.CONSTANT) {
            String nameString = constant.name.value;
            BLangConstantValue value = constant.symbol.value;

            if (constantMap.containsKey(nameString)) {
                if (value == null) {
                    dlog.error(constant.name.pos, DiagnosticErrorCode.ALREADY_INITIALIZED_SYMBOL, nameString);
                } else {
                    BLangConstantValue lastValue = constantMap.get(nameString);
                    if (!value.equals(lastValue)) {
                        if (lastValue == null) {
                            dlog.error(constant.name.pos, DiagnosticErrorCode.ALREADY_INITIALIZED_SYMBOL, nameString);
                        } else {
                            dlog.error(constant.name.pos, DiagnosticErrorCode.ALREADY_INITIALIZED_SYMBOL_WITH_ANOTHER,
                                    nameString, lastValue);
                        }
                    }
                }
            } else {
                constantMap.put(nameString, value);
            }
        }
    }

    private void defineConstant(SymbolEnv symEnv, Map<String, BLangNode> modTable, BLangConstant constant) {
        BType staticType;
        constant.symbol = symEnter.getConstantSymbol(constant);
        BLangTypeDefinition typeDef = constant.associatedTypeDefinition;
        NodeKind nodeKind = constant.expr.getKind();
        boolean isLiteral = nodeKind == NodeKind.LITERAL || nodeKind == NodeKind.NUMERIC_LITERAL
                || nodeKind == NodeKind.UNARY_EXPR;
        if (typeDef != null && isLiteral) {
            resolveTypeDefinition(symEnv, modTable, typeDef, 0);
        }
        if (constant.typeNode != null) {
            // Type node is available.
            staticType = resolveTypeDesc(symEnv, modTable, typeDef, 0, constant.typeNode);
            if (staticType == symTable.noType) {
                constant.setBType(symTable.semanticError);
                return;
            }
        } else {
            staticType = symTable.noType;
        }

        BConstantSymbol constantSymbol = symEnter.getConstantSymbol(constant);
        constant.symbol = constantSymbol;

        ConstantTypeChecker.AnalyzerData data = new ConstantTypeChecker.AnalyzerData();
        data.constantSymbol = constantSymbol;
        data.env = symEnv;
        data.modTable = modTable;
        data.expType = staticType;
        // Type check and resolve the constant expression.
        BType inferredExpType = constantTypeChecker.checkConstExpr(constant.expr, staticType, data);

        if (inferredExpType == symTable.semanticError) {
            // Constant expression contains errors.
            constant.setBType(symTable.semanticError);
            constantSymbol.type = symTable.semanticError;
            symEnv.scope.define(constantSymbol.name, constantSymbol);
            return;
        }

        if (inferredExpType.tag == TypeTags.FINITE) {
            inferredExpType.tsymbol.originalName = names.originalNameFromIdNode(constant.name);
        }

        // Get immutable type for the narrowed type.
        BType intersectionType = ImmutableTypeCloner.getImmutableType(constant.pos, types, inferredExpType, symEnv,
                symEnv.scope.owner.pkgID, symEnv.scope.owner, symTable, anonymousModelHelper, names,
                new HashSet<>());

        // Fix the constant expr types due to tooling requirements.
        resolveConstantExpressionType.resolveConstExpr(constant.expr, intersectionType, data);

        // Update the final type in necessary fields.
        constantSymbol.type = intersectionType;
        if (intersectionType.tag == TypeTags.FINITE) {
            constantSymbol.literalType = ((BFiniteType) intersectionType).getValueSpace().iterator().next().getBType();
        } else {
            constantSymbol.literalType = intersectionType;
        }
        constant.setBType(intersectionType);

        // Get the constant value from the final type.
        constantSymbol.value = constantTypeChecker.getConstantValue(intersectionType);

        if (isLiteral && constantSymbol.type.tag != TypeTags.TYPEREFDESC && typeDef != null) {
            // Update flags.
            constantSymbol.type.tsymbol.flags |= typeDef.symbol.flags;
        }

        constantSymbol.markdownDocumentation = symEnter.getMarkdownDocAttachment(constant.markdownDocumentationAttachment);
        if (symEnter.isDeprecated(constant.annAttachments)) {
            constantSymbol.flags |= Flags.DEPRECATED;
        }
        // Add the symbol to the enclosing scope.
        if (!symResolver.checkForUniqueSymbol(constant.name.pos, symEnv, constantSymbol)) {
            return;
        }

        if (constant.symbol.name == Names.IGNORE) {
            // Avoid symbol definition for constants with name '_'
            return;
        }
        // Add the symbol to the enclosing scope.
        BLangTypeDefinition typeDefinition = findTypeDefinition(symEnv.enclPkg.typeDefinitions,
                inferredExpType.tsymbol.name.value);
        if (typeDefinition != null && constant.associatedTypeDefinition == null) {
            constant.associatedTypeDefinition = typeDefinition;
        }
        constant.setDeterminedType(null);
        symEnv.scope.define(constantSymbol.name, constantSymbol);
    }

    public BLangTypeDefinition findTypeDefinition(List<BLangTypeDefinition> typeDefinitionArrayList, String name) {
        for (int i = typeDefinitionArrayList.size() - 1; i >= 0; i--) {
            BLangTypeDefinition typeDefinition = typeDefinitionArrayList.get(i);
            if (typeDefinition.name.value.contains(name)) {
                return typeDefinition;
            }
        }
        return null;
    }

    private void defineErrorType(Location pos, BErrorType errorType, SymbolEnv env) {
        SymbolEnv pkgEnv = symTable.pkgEnvMap.get(env.enclPkg.symbol);
        BTypeSymbol errorTSymbol = errorType.tsymbol;
        errorTSymbol.scope = new Scope(errorTSymbol);

        if (symResolver.checkForUniqueSymbol(pos, pkgEnv, errorTSymbol)) {
            pkgEnv.scope.define(errorTSymbol.name, errorTSymbol);
        }
    }

    /**
     * Used to store location data for encountered unknown types in `checkErrors` method.
     *
     * @since 0.985.0
     */
    class LocationData {

        private String name;
        private int row;
        private int column;

        LocationData(String name, int row, int column) {
            this.name = name;
            this.row = row;
            this.column = column;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LocationData that = (LocationData) o;
            return row == that.row &&
                    column == that.column &&
                    name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, row, column);
        }
    }
}
