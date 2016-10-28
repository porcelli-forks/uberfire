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

package org.uberfire.java.nio.fs.jgit.util.commands;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * TODO: update me
 */
public class ResolveRevCommit {

    private final Repository repo;
    private final ObjectId objectId;

    public ResolveRevCommit( final Repository repo,
                             final ObjectId objectId ) {
        this.repo = repo;
        this.objectId = objectId;
    }

    public RevCommit execute() throws IOException {
        try ( final ObjectReader reader = repo.newObjectReader() ) {
            return RevCommit.parse( reader.open( objectId ).getBytes() );
        }

    }
}
