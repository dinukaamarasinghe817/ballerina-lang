/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.shell.utils;

import io.ballerina.tools.text.LinePosition;
import io.ballerina.tools.text.TextDocument;

import java.util.StringJoiner;

/**
 * Utility functions required by invokers.
 * Static class.
 *
 * @since 2.0.0
 */
public class StringUtils {
    private static final int MAX_VAR_STRING_LENGTH = 78;
    private static final String QUOTE = "'";
    private static final String SPACE = " ";
    private static final String CARET = "^";
    private static final String DASH = "-";

    /**
     * Creates an quoted identifier to use for variable names.
     *
     * @param identifier Identifier without quote.
     * @return Quoted identifier.
     */
    public static String quoted(String identifier) {
        if (String.valueOf(identifier).startsWith(QUOTE)) {
            return identifier;
        }
        return QUOTE + identifier;
    }

    /**
     * Short a string to a certain length.
     *
     * @param input Input string to shorten.
     * @return Shortened string.
     */
    public static String shortenedString(Object input) {
        String value = String.valueOf(input);
        value = value.replaceAll("\n", "");
        if (value.length() > MAX_VAR_STRING_LENGTH) {
            int subStrLength = MAX_VAR_STRING_LENGTH / 2;
            return value.substring(0, subStrLength)
                    + "..." + value.substring(value.length() - subStrLength);
        }
        return value;
    }

    /**
     * Highlight and show the error position.
     * The highlighted text would follow format,
     * <pre>
     * incompatible types: expected 'string', found 'int'
     *     int i = "Hello";
     *             ^-----^
     * </pre>
     * However if the error is multiline, it would follow following format,
     * <pre>
     *
     * </pre>
     *
     * @param textDocument Text document to extract source code.
     * @param diagnostic   Diagnostic to show.
     * @return The string with position highlighted.
     */
    public static String highlightDiagnostic(TextDocument textDocument,
                                             io.ballerina.tools.diagnostics.Diagnostic diagnostic) {
        LinePosition startLine = diagnostic.location().lineRange().startLine();
        LinePosition endLine = diagnostic.location().lineRange().endLine();

        if (startLine.line() != endLine.line()) {
            // Error spans for several lines, will not highlight error
            StringJoiner errorMessage = new StringJoiner("\n\t");
            errorMessage.add(diagnostic.message());
            for (int i = startLine.line(); i <= endLine.line(); i++) {
                errorMessage.add(textDocument.line(i).text().strip());
            }
            return errorMessage.toString();
        }

        // Error is same line, can highlight using ^-----^
        // Error will expand as ^, ^^, ^-^, ^--^
        int position = startLine.offset();
        int length = Math.max(endLine.offset() - startLine.offset(), 1);
        String caretUnderline = length == 1
                ? CARET : CARET + DASH.repeat(length - 2) + CARET;

        // Get the source code
        String sourceLine = textDocument.line(startLine.line()).text();

        // Count leading spaces
        int leadingSpaces = sourceLine.length() - sourceLine.stripLeading().length();
        String strippedSourceLine = sourceLine.substring(leadingSpaces);

        // Result should be padded with a tab
        return String.format("%s%n\t%s%n\t%s%s", diagnostic.message(), strippedSourceLine,
                SPACE.repeat(position - leadingSpaces), caretUnderline);
    }
}
