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

package org.uberfire.java.nio.fs.jgit;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import org.eclipse.jgit.api.Git;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.uberfire.java.nio.fs.jgit.util.JGitUtil;

import static org.junit.Assert.*;
import static org.uberfire.java.nio.fs.jgit.util.JGitUtil.*;

@RunWith(org.jboss.byteman.contrib.bmunit.BMUnitRunner.class)
@BMUnitConfig(loadDirectory = "target/test-classes", debug = true) // set "debug=true to see debug output
public class ConcurrentJGitUtilTest extends AbstractTestInfra {

    @Test
    @BMScript(value = "byteman/fail_on_treewalk.btm")
    public void testCommitAndResolve() throws IOException {

        // RHBPMS-4105
        final File parentFolder = createTempDirectory();
        final File gitFolder = new File( parentFolder, "mytest.git" );

        final Git git = JGitUtil.newRepository( gitFolder, true );

        commit( git, "master", "name", "name@example.com", "1st commit", null, new Date(), false, new HashMap<String, File>() {
            {
                put( "path/to/file1.txt", tempFile( "temp2222" ) );
            }
        } );
        commit( git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false, new HashMap<String, File>() {
            {
                put( "path/to/file2.txt", tempFile( "temp2222" ) );
            }
        } );

        try {
            JGitUtil.resolvePath( git, "master", "path/to/file1.txt" );
            JGitUtil.resolvePath( git, "master", "path/to/file1.txt" );
        } catch ( Exception ex ){
            if (ex.getMessage().equals( "almost random failure" )){
                fail();
            }
        }
    }
}