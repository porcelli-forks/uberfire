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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ListCommits {

    private final Repository repo;
    private final ObjectId startRange;
    private final ObjectId endRange;

    public ListCommits( final Repository repo,
                        final ObjectId startRange,
                        final ObjectId endRange ) {
        this.repo = repo;
        this.startRange = startRange;
        this.endRange = endRange;
    }

    public List<RevCommit> execute() throws IOException {
        final List<RevCommit> list = new ArrayList<>();
        try ( final RevWalk rw = new RevWalk( repo ) ) {
            // resolve branch
            rw.markStart( rw.parseCommit( endRange ) );
            if ( startRange != null ) {
                rw.markUninteresting( rw.parseCommit( startRange ) );
            }
            for ( RevCommit rev : rw ) {
                list.add( rev );
            }
            return list;
        } catch ( final Exception ex ) {
            throw ex;
        }

    }

}
