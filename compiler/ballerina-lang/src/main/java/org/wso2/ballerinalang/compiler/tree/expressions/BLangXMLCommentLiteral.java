/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.ballerinalang.compiler.tree.expressions;

import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.expressions.ExpressionNode;
import org.ballerinalang.model.tree.expressions.XMLCommentLiteralNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeAnalyzer;
import org.wso2.ballerinalang.compiler.tree.BLangNodeModifier;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * @since 0.94
 */
public class BLangXMLCommentLiteral extends BLangExpression implements XMLCommentLiteralNode {

    // BLangNodes
    public List<BLangExpression> textFragments;
    public BLangExpression concatExpr; // TODO: #AST_CLEAN

    public BLangXMLCommentLiteral() {
        textFragments = new ArrayList<BLangExpression>();
    }

    @Override
    public List<BLangExpression> getTextFragments() {
        return textFragments;
    }

    @Override
    public void addTextFragment(ExpressionNode textFragment) {
        this.textFragments.add((BLangExpression) textFragment);
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <T> void accept(BLangNodeAnalyzer<T> analyzer, T props) {
        analyzer.visit(this, props);
    }

    @Override
    public <T, R> R apply(BLangNodeModifier<T, R> modifier, T props) {
        return modifier.modify(this, props);
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.XML_COMMENT_LITERAL;
    }

    @Override
    public String toString() {
        return "BLangXMLCommentLiteral: " + textFragments;
    }
}
