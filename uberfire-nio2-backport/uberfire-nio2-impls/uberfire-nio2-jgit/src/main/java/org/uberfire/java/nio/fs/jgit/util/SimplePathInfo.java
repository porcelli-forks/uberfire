/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.java.nio.fs.jgit.util;

import org.eclipse.jgit.lib.ObjectId;

/**
 * TODO: update me
 */
public class SimplePathInfo {

    private final ObjectId objectId;
    private final String path;
    private final JGitUtil.PathType pathType;

    public SimplePathInfo( final ObjectId objectId,
                           final String path,
                           final JGitUtil.PathType pathType ) {
        this.objectId = objectId;
        this.path = path;
        this.pathType = pathType;
    }

    public ObjectId getObjectId() {
        return objectId;
    }

    public String getPath() {
        return path;
    }

    public JGitUtil.PathType getPathType() {
        return pathType;
    }
}
