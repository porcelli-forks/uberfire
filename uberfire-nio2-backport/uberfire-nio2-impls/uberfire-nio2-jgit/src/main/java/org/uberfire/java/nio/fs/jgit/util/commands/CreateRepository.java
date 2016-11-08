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

import java.io.File;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.uberfire.java.nio.IOException;

public class CreateRepository {

    private final File repoDir;
    private final File hookDir;

    public CreateRepository( final File repoDir ) {
        this( repoDir, null );
    }

    public CreateRepository( final File repoDir,
                             final File hookDir ) {
        this.repoDir = repoDir;
        this.hookDir = hookDir;
    }

    public Optional<Git> execute() {
        try {
            final Git git = Git.init().setBare( true ).setDirectory( repoDir ).call();

            if ( hookDir != null ) {
                final File repoHookDir = new File( repoDir, "hooks" );

                try {
                    FileUtils.copyDirectory( hookDir, repoHookDir );
                } catch ( final Exception ex ) {
                    throw new RuntimeException( ex );
                }

                for ( final File file : repoHookDir.listFiles() ) {
                    if ( file != null && file.isFile() ) {
                        file.setExecutable( true );
                    }
                }
            }

            return Optional.of( git );
        } catch ( final Exception ex ) {
            throw new IOException( ex );
        }
    }
}
