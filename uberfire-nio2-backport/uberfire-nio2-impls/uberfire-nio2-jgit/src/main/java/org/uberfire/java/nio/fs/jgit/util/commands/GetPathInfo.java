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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.uberfire.java.nio.fs.jgit.util.JGitUtil;
import org.uberfire.java.nio.fs.jgit.util.SimplePathInfo;

import static org.uberfire.java.nio.fs.jgit.util.JGitUtil.*;

public class GetPathInfo {

    private final Git git;
    private final String branchName;
    private final String path;

    public GetPathInfo( final Git git,
                        final String branchName,
                        final String path ) {
        this.git = git;
        this.branchName = branchName;
        this.path = path;
    }

    public SimplePathInfo execute() throws IOException {

        final String gitPath = normalizePath( path );

        if ( gitPath.isEmpty() ) {
            return new SimplePathInfo( null, path, JGitUtil.PathType.DIRECTORY );
        }

        final ObjectId tree = getTreeRefObjectId( git.getRepository(), branchName );
        if ( tree == null ) {
            return new SimplePathInfo( null, path, JGitUtil.PathType.NOT_FOUND );
        }
        try ( final TreeWalk tw = new TreeWalk( git.getRepository() ) ) {
            tw.setFilter( PathFilter.create( gitPath ) );
            tw.reset( tree );
            while ( tw.next() ) {
                if ( tw.getPathString().equals( gitPath ) ) {
                    if ( tw.getFileMode( 0 ).equals( FileMode.TYPE_TREE ) ) {
                        return new SimplePathInfo( tw.getObjectId( 0 ), path, JGitUtil.PathType.DIRECTORY );
                    } else if ( tw.getFileMode( 0 ).equals( FileMode.TYPE_FILE ) ||
                            tw.getFileMode( 0 ).equals( FileMode.EXECUTABLE_FILE ) ||
                            tw.getFileMode( 0 ).equals( FileMode.REGULAR_FILE ) ) {
                        return new SimplePathInfo( tw.getObjectId( 0 ), path, JGitUtil.PathType.FILE );
                    }
                }
                if ( tw.isSubtree() ) {
                    tw.enterSubtree();
                }
            }
        } catch ( final Throwable ex ) {
            throw ex;
        }
        return new SimplePathInfo( null, path, JGitUtil.PathType.NOT_FOUND );

    }

}
