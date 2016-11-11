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

import java.io.InputStream;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.commons.config.ConfigProperties;
import org.uberfire.java.nio.file.NoSuchFileException;
import org.uberfire.java.nio.fs.jgit.util.commands.BlobAsInputStream;
import org.uberfire.java.nio.fs.jgit.util.commands.GetLastCommit;
import org.uberfire.java.nio.fs.jgit.util.commands.GetPathInfo;
import org.uberfire.java.nio.fs.jgit.util.commands.ListCommits;
import org.uberfire.java.nio.fs.jgit.util.commands.ListPathContent;
import org.uberfire.java.nio.fs.jgit.util.commands.PathUtil;
import org.uberfire.java.nio.fs.jgit.util.model.PathInfo;

import static org.uberfire.commons.validation.Preconditions.*;

public final class RetryUtil {

    private static final Logger LOG = LoggerFactory.getLogger( RetryUtil.class );
    private static final String DEFAULT_JGIT_RETRY_SLEEP_TIME = "50";
    private static int JGIT_RETRY_TIMES = initRetryValue();
    private static final int JGIT_RETRY_SLEEP_TIME = initSleepTime();

    private static int initSleepTime() {
        final ConfigProperties config = new ConfigProperties( System.getProperties() );
        return config.get( "org.uberfire.nio.git.retry.onfail.sleep", DEFAULT_JGIT_RETRY_SLEEP_TIME ).getIntValue();
    }

    private static int initRetryValue() {
        final ConfigProperties config = new ConfigProperties( System.getProperties() );
        final String osName = config.get( "os.name", "any" ).getValue();
        final String defaultRetryTimes;
        if ( osName.toLowerCase().contains( "windows" ) ) {
            defaultRetryTimes = "10";
        } else {
            defaultRetryTimes = "0";
        }
        try {
            return config.get( "org.uberfire.nio.git.retry.onfail.times", defaultRetryTimes ).getIntValue();
        } catch ( NumberFormatException ex ) {
            return 0;
        }
    }

    //just for test purposes
    static void setRetryTimes( int retryTimes ) {
        JGIT_RETRY_TIMES = retryTimes;
    }

    private RetryUtil() {
    }

    public static InputStream resolveInputStream( final Git git,
                                                  final String treeRef,
                                                  final String path ) {
        checkNotNull( "git", git );
        checkNotEmpty( "treeRef", treeRef );
        checkNotEmpty( "path", path );

        return retryIfNeeded( NoSuchFileException.class,
                              () -> new BlobAsInputStream( git.getRepository(),
                                                           treeRef,
                                                           PathUtil.normalize( path ) ).execute().get() );
    }

    public static RevCommit getLastCommit( final Git git,
                                           final String branchName ) {
        return retryIfNeeded( RuntimeException.class, () -> new GetLastCommit( git.getRepository(), branchName ).execute() );
    }

    public static List<RevCommit> listCommits( final Git git,
                                               final ObjectId startRange,
                                               final ObjectId endRange ) {
        return retryIfNeeded( RuntimeException.class, () -> new ListCommits( git.getRepository(), startRange, endRange ).execute() );
    }

    private static <E extends Throwable, T> T retryIfNeeded( final Class<E> eclazz,
                                                             final ThrowableSupplier<T> supplier ) throws E {
        int i = 0;
        do {
            try {
                return supplier.get();
            } catch ( final Throwable ex ) {
                if ( i < ( JGIT_RETRY_TIMES - 1 ) ) {
                    try {
                        Thread.sleep( JGIT_RETRY_SLEEP_TIME );
                    } catch ( final InterruptedException ignored ) {
                    }
                    LOG.debug( String.format( "Unexpected exception (%d/%d).", i + 1, JGIT_RETRY_TIMES ), ex );
                } else {
                    LOG.error( String.format( "Unexpected exception (%d/%d).", i + 1, JGIT_RETRY_TIMES ), ex );
                    if ( ex.getClass().isAssignableFrom( eclazz ) ) {
                        throw (E) ex;
                    }
                    throw new RuntimeException( ex );
                }
            }

            i++;
        } while ( i < JGIT_RETRY_TIMES );

        return null;
    }

    public static PathInfo getPathInfo( final Git git,
                                        final String branchName,
                                        final String path ) {
        checkNotNull( "git", git );
        checkNotNull( "path", path );
        checkNotEmpty( "branchName", branchName );

        return retryIfNeeded( RuntimeException.class, () -> new GetPathInfo( git, branchName, path ).execute() );
    }

    public static List<PathInfo> listPathContent( final Git git,
                                                  final String branchName,
                                                  final String path ) {
        checkNotNull( "git", git );
        checkNotNull( "path", path );
        checkNotEmpty( "branchName", branchName );

        return retryIfNeeded( RuntimeException.class, () -> new ListPathContent( git, branchName, path ).execute() );
    }

}
