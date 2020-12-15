/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerina.projects.directory;

import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.projects.util.ProjectPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Contains a set of utility methods to create a project.
 *
 * @since 2.0.0
 */
public class ProjectLoader {

    public static Project loadProject(Path path) {
        return loadProject(path, ProjectEnvironmentBuilder.getDefaultBuilder());
    }

    /**
     * Returns a project by deriving the type from the path provided.
     *
     * @param path ballerina project or standalone file path
     * @return project of applicable type
     */
    public static Project loadProject(Path path, ProjectEnvironmentBuilder projectEnvironmentBuilder) {
        Path absFilePath = Optional.of(path.toAbsolutePath()).get();
        Path projectRoot;
        if (!Files.exists(path)) {
            throw new ProjectException("provided file path does not exist");
        }
        if (absFilePath.toFile().isDirectory()) {
            if (ProjectConstants.MODULES_ROOT.equals(
                    Optional.of(absFilePath.getParent()).get().toFile().getName())) {
                projectRoot = Optional.of(Optional.of(absFilePath.getParent()).get().getParent()).get();
            } else {
                projectRoot = absFilePath;
            }
            return BuildProject.load(projectEnvironmentBuilder, projectRoot);
        }

        if (ProjectPaths.isBallerinaStandaloneFile(absFilePath)) {
            return SingleFileProject.load(projectEnvironmentBuilder, absFilePath);
        }

        try {
            projectRoot = ProjectPaths.findPackageRoot(absFilePath);
            return BuildProject.load(projectEnvironmentBuilder, projectRoot);
        } catch (ProjectException e) {
            return SingleFileProject.load(projectEnvironmentBuilder, path);
        }
    }
}
