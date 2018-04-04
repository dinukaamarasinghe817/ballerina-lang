/**
 * Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver;

import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Package context to keep the builtin and the current package.
 */
public class LSPackageCache {

    private final ExtendedPackageCache packageCache;

    private static final String[] staticPkgNames = {"http", "http.swagger", "net.uri", "mime", "auth", "auth.authz",
            "auth.authz.permissionstore", "auth.basic", "auth.jwtAuth", "auth.userstore", "auth.utils", "caching",
            "collections", "config", "sql", "file", "internal", "io", "jwt", "jwt.signature", "log", "math", "os",
            "reflect", "runtime", "security.crypto", "task", "time", "transactions", "user", "util"};

    public LSPackageCache() {
        CompilerContext tempCompilerContext = CommonUtil.prepareTempCompilerContext();
        packageCache = new ExtendedPackageCache(tempCompilerContext);
        List<BLangPackage> builtInPackages = LSPackageLoader.getBuiltinPackages();
        builtInPackages.forEach(this::addPackage);
        this.loadPackagesMap(tempCompilerContext);
    }

    /**
     * Find the package by Package ID.
     * @param compilerContext       compiler context
     * @param pkgId                 Package Id to lookup
     * @return {@link BLangPackage} BLang Package resolved
     */
    public BLangPackage findPackage(CompilerContext compilerContext, PackageID pkgId) {
        BLangPackage bLangPackage = packageCache.get(pkgId);
        if (bLangPackage != null) {
            return bLangPackage;
        } else {
            bLangPackage = LSPackageLoader.getPackageById(compilerContext, pkgId);
            addPackage(bLangPackage);
            return bLangPackage;
        }
    }

    /**
     * add package to the package map.
     *
     * @param bLangPackage ballerina package to be added.
     */
    void addPackage(BLangPackage bLangPackage) {
        if (bLangPackage.getPackageDeclaration() == null) {
            //TODO check whether getPackageDeclaration() is needed
            this.packageCache.put(new PackageID(Names.DOT.value), bLangPackage);
        } else {
            this.packageCache.put(bLangPackage.packageID, bLangPackage);
        }
    }
    
    private void loadPackagesMap(CompilerContext tempCompilerContext) {
        for (String staticPkgName : staticPkgNames) {
            PackageID packageID = new PackageID(new org.wso2.ballerinalang.compiler.util.Name("ballerina"),
                    new org.wso2.ballerinalang.compiler.util.Name(staticPkgName),
                    new org.wso2.ballerinalang.compiler.util.Name("0.0.0"));
            
            this.packageCache.put(packageID, LSPackageLoader.getPackageById(tempCompilerContext, packageID));
        }
    }

    public Map<String, BLangPackage> getPackageMap() {
        return packageCache.getMap();
    }

    static class ExtendedPackageCache extends PackageCache {

        private ExtendedPackageCache(CompilerContext context) {
            super(context);
            this.packageMap = new ConcurrentHashMap<>();
        }

        public Map<String, BLangPackage> getMap() {
            return this.packageMap;
        }
    }
}
