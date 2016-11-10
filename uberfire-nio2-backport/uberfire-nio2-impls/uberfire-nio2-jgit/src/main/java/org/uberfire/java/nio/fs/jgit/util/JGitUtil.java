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

import java.io.File;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.commons.config.ConfigProperties;
import org.uberfire.java.nio.base.FileTimeImpl;
import org.uberfire.java.nio.base.attributes.HiddenAttributes;
import org.uberfire.java.nio.base.attributes.HiddenAttributesImpl;
import org.uberfire.java.nio.base.version.VersionAttributes;
import org.uberfire.java.nio.base.version.VersionHistory;
import org.uberfire.java.nio.base.version.VersionRecord;
import org.uberfire.java.nio.file.NoSuchFileException;
import org.uberfire.java.nio.file.attribute.BasicFileAttributes;
import org.uberfire.java.nio.file.attribute.FileTime;
import org.uberfire.java.nio.fs.jgit.CommitInfo;
import org.uberfire.java.nio.fs.jgit.JGitFileSystem;
import org.uberfire.java.nio.fs.jgit.daemon.filters.HiddenBranchRefFilter;
import org.uberfire.java.nio.fs.jgit.util.commands.BlobAsInputStream;
import org.uberfire.java.nio.fs.jgit.util.commands.Commit;
import org.uberfire.java.nio.fs.jgit.util.commands.GetPathInfo;
import org.uberfire.java.nio.fs.jgit.util.commands.ListCommits;

import static org.eclipse.jgit.lib.Constants.*;
import static org.eclipse.jgit.lib.FileMode.*;
import static org.uberfire.commons.validation.Preconditions.*;

public final class JGitUtil {

    private static final Logger LOG = LoggerFactory.getLogger( JGitUtil.class );
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

    private JGitUtil() {
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
                                                           normalizePath( path ) ).execute().get() );
    }

    public static String normalizePath( final String path ) {

        if ( path.equals( "/" ) ) {
            return "";
        }

        final boolean startsWith = path.startsWith( "/" );
        final boolean endsWith = path.endsWith( "/" );
        if ( startsWith && endsWith ) {
            return path.substring( 1, path.length() - 1 );
        }
        if ( startsWith ) {
            return path.substring( 1 );
        }
        if ( endsWith ) {
            return path.substring( 0, path.length() - 1 );
        }
        return path;
    }

    public static ObjectId getTreeRefObjectId( final Repository repo,
                                               final String treeRef ) {
        try {
            final RevCommit commit = getLastCommit( repo, treeRef );
            if ( commit == null ) {
                return null;
            }
            return commit.getTree().getId();
        } catch ( java.io.IOException ex ) {
            throw new RuntimeException( ex );
        }
    }

    public static void refUpdate( final Repository repository,
                                  final String branchName,
                                  final ObjectId headId,
                                  final RevCommit revCommit,
                                  final String refLogPrefix ) throws java.io.IOException, ConcurrentRefUpdateException {

        final RefUpdate ru = repository.updateRef( Constants.R_HEADS + branchName );
        if ( headId == null ) {
            ru.setExpectedOldObjectId( ObjectId.zeroId() );
        } else {
            ru.setExpectedOldObjectId( headId );
        }
        ru.setNewObjectId( revCommit.getId() );
        ru.setRefLogMessage( refLogPrefix + revCommit.getShortMessage(), false );
        forceUpdate( ru, revCommit.getId() );
    }

    public static void forceUpdate( final RefUpdate ru,
                                    final ObjectId id ) throws java.io.IOException, ConcurrentRefUpdateException {
        final RefUpdate.Result rc = ru.forceUpdate();
        switch ( rc ) {
            case NEW:
            case FORCED:
            case FAST_FORWARD:
                break;
            case REJECTED:
            case LOCK_FAILURE:
                throw new ConcurrentRefUpdateException( JGitText.get().couldNotLockHEAD, ru.getRef(), rc );
            default:
                throw new JGitInternalException( MessageFormat.format( JGitText.get().updatingRefFailed, Constants.HEAD, id.toString(), rc ) );
        }
    }

    public static RevCommit getLastCommit( final Git git,
                                           final String branchName ) {
        return retryIfNeeded( RuntimeException.class, () -> getLastCommit( git.getRepository(), branchName ) );
    }

    public static RevCommit resolveRevCommit( final Repository repository,
                                              final ObjectId objectId ) throws java.io.IOException {
        try ( final ObjectReader reader = repository.newObjectReader() ) {
            return RevCommit.parse( reader.open( objectId ).getBytes() );
        }
    }

    private static RevCommit getLastCommit( final Repository repo,
                                            final String branchName ) throws java.io.IOException {
        try {
            final Ref ref = getBranch( repo, branchName );
            if ( ref == null ) {
                return null;
            }
            return resolveRevCommit( repo, ref.getTarget().getObjectId() );
        } catch ( final Exception ex ) {
            throw ex;
        }
    }

    public static List<Ref> branchList( final Git git ) {
        checkNotNull( "git", git );
        try {
            return new ArrayList<>( git.getRepository().getRefDatabase().getRefs( "refs/heads/" ).values() );
        } catch ( java.io.IOException e ) {
            throw new RuntimeException( e );
        }
    }

    public static Ref getBranch( final Repository repo,
                                 final String name ) {

        try {
            return repo.getRefDatabase().getRef( name );
        } catch ( java.io.IOException e ) {
        }

        return null;
    }

    public static List<RevCommit> listCommits( final Git git,
                                               final ObjectId startRange,
                                               final ObjectId endRange ) {
        return retryIfNeeded( RuntimeException.class, () -> new ListCommits( git.getRepository(), startRange, endRange ).execute() );
    }

    public static void commit( final Git git,
                               final String branchName,
                               final String name,
                               final String email,
                               final String message,
                               final TimeZone timeZone,
                               final Date when,
                               final boolean amend,
                               final Map<String, File> content ) {
        commit( git, branchName, new CommitInfo( null, name, email, message, timeZone, when ), amend, new DefaultCommitContent( content ) );
    }

    public static boolean commit( final Git git,
                                  final String branchName,
                                  final CommitInfo commitInfo,
                                  final boolean amend,
                                  final CommitContent content ) {
        if ( content instanceof RevertCommitContent ) {
            return commit( git, branchName, commitInfo, amend, resolveObjectId( git, ( (RevertCommitContent) content ).getRefTree() ), content );
        } else {
            return commit( git, branchName, commitInfo, amend, null, content );
        }
    }

    private static boolean commit( final Git git,
                                   final String branchName,
                                   final CommitInfo commitInfo,
                                   final boolean amend,
                                   final ObjectId originId,
                                   final CommitContent content ) {
        return new Commit( git, branchName, commitInfo, amend, originId, content ).execute();
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

    public static ObjectId resolveObjectId( final Git git,
                                            final String name ) {

        final ObjectId[] result = resolveObjectIds( git, name );
        if ( result.length == 0 ) {
            return null;
        }

        return result[ 0 ];
    }

    public static ObjectId[] resolveObjectIds( final Git git,
                                               final String... ids ) {
        final Collection<ObjectId> result = new ArrayList<>();
        for ( final String id : ids ) {
            try {
                final Ref refName = getBranch( git.getRepository(), id );
                if ( refName != null ) {
                    result.add( refName.getObjectId() );
                    continue;
                }

                try {
                    final ObjectId _id = ObjectId.fromString( id );
                    if ( git.getRepository().getObjectDatabase().has( _id ) ) {
                        result.add( _id );
                    }
                } catch ( final IllegalArgumentException ignored ) {
                }
            } catch ( final java.io.IOException ignored ) {
            }
        }

        return result.toArray( new ObjectId[ result.size() ] );
    }

    public static VersionAttributes buildVersionAttributes( final JGitFileSystem fs,
                                                            final String branchName,
                                                            final String path ) {
        final PathInfo pathInfo = resolvePath( fs.getGit(), branchName, path );

        if ( pathInfo == null ) {
            throw new NoSuchFileException( path );
        }

        final String gPath = normalizePath( path );

        final ObjectId id = resolveObjectId( fs.getGit(), branchName );

        final List<VersionRecord> records = new ArrayList<>();

        if ( id != null ) {
            try {
                final LogCommand logCommand = fs.getGit().log().add( id );
                if ( !gPath.isEmpty() ) {
                    logCommand.addPath( gPath );
                }

                for ( final RevCommit commit : logCommand.call() ) {

                    records.add( new VersionRecord() {
                        @Override
                        public String id() {
                            return commit.name();
                        }

                        @Override
                        public String author() {
                            return commit.getAuthorIdent().getName();
                        }

                        @Override
                        public String email() {
                            return commit.getAuthorIdent().getEmailAddress();
                        }

                        @Override
                        public String comment() {
                            return commit.getFullMessage();
                        }

                        @Override
                        public Date date() {
                            return commit.getAuthorIdent().getWhen();
                        }

                        @Override
                        public String uri() {
                            return fs.getPath( commit.name(), path ).toUri().toString();
                        }
                    } );
                }
            } catch ( Exception e ) {
                throw new RuntimeException( e );
            }
        }

        Collections.reverse( records );

        return new VersionAttributes() {
            @Override
            public VersionHistory history() {
                return () -> records;
            }

            @Override
            public FileTime lastModifiedTime() {
                if ( records.size() > 0 ) {
                    return new FileTimeImpl( records.get( records.size() - 1 ).date().getTime() );
                }
                return null;
            }

            @Override
            public FileTime lastAccessTime() {
                return lastModifiedTime();
            }

            @Override
            public FileTime creationTime() {
                if ( records.size() > 0 ) {
                    return new FileTimeImpl( records.get( 0 ).date().getTime() );
                }
                return null;
            }

            @Override
            public boolean isRegularFile() {
                return pathInfo.getPathType().equals( PathType.FILE );
            }

            @Override
            public boolean isDirectory() {
                return pathInfo.getPathType().equals( PathType.DIRECTORY );
            }

            @Override
            public boolean isSymbolicLink() {
                return false;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return pathInfo.getSize();
            }

            @Override
            public Object fileKey() {
                return pathInfo.getObjectId() == null ? null : pathInfo.getObjectId().toString();
            }

        };
    }

    public static BasicFileAttributes buildBasicAttributes( final JGitFileSystem fs,
                                                            final String branchName,
                                                            final String path ) {
        final PathInfo pathInfo = resolvePath( fs.getGit(), branchName, path );

        if ( pathInfo == null ) {
            throw new NoSuchFileException( path );
        }

        final ObjectId id = resolveObjectId( fs.getGit(), branchName );
        final String gPath = normalizePath( path );

        return new BasicFileAttributes() {

            private long lastModifiedDate = -1;
            private long creationDate = -1;

            @Override
            public FileTime lastModifiedTime() {
                if ( lastModifiedDate == -1L ) {
                    RevWalk revWalk = null;
                    try {
                        final LogCommand logCommand = fs.getGit().log().add( id ).setMaxCount( 1 );
                        if ( !gPath.isEmpty() ) {
                            logCommand.addPath( gPath );
                        }
                        revWalk = (RevWalk) logCommand.call();
                        lastModifiedDate = revWalk.iterator().next().getCommitterIdent().getWhen().getTime();
                    } catch ( Exception ex ) {
                        lastModifiedDate = 0;
                    } finally {
                        if ( revWalk != null ) {
                            revWalk.dispose();
                        }
                    }
                }
                return new FileTimeImpl( lastModifiedDate );
            }

            @Override
            public FileTime lastAccessTime() {
                return lastModifiedTime();
            }

            @Override
            public FileTime creationTime() {
                if ( creationDate == -1L ) {
                    RevWalk revWalk = null;
                    try {
                        final LogCommand logCommand = fs.getGit().log().add( id ).setMaxCount( 1 );
                        if ( !gPath.isEmpty() ) {
                            logCommand.addPath( gPath );
                        }
                        revWalk = (RevWalk) logCommand.call();
                        creationDate = revWalk.iterator().next().getCommitterIdent().getWhen().getTime();
                    } catch ( Exception ex ) {
                        creationDate = 0;
                    } finally {
                        if ( revWalk != null ) {
                            revWalk.dispose();
                        }
                    }
                }
                return new FileTimeImpl( creationDate );
            }

            @Override
            public boolean isRegularFile() {
                return pathInfo.getPathType().equals( PathType.FILE );
            }

            @Override
            public boolean isDirectory() {
                return pathInfo.getPathType().equals( PathType.DIRECTORY );
            }

            @Override
            public boolean isSymbolicLink() {
                return false;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return pathInfo.getSize();
            }

            @Override
            public Object fileKey() {
                return pathInfo.getObjectId() == null ? null : pathInfo.getObjectId().toString();
            }

        };
    }

    public static boolean hasBranch( final Git git,
                                     final String branchName ) {
        checkNotNull( "git", git );
        checkNotEmpty( "branchName", branchName );

        return getBranch( git.getRepository(), branchName ) != null;
    }

    public static HiddenAttributes buildHiddenAttributes( final JGitFileSystem fileSystem,
                                                          final String branchName,
                                                          final String path ) {

        final BasicFileAttributes attributes = buildBasicAttributes( fileSystem, branchName, path );

        return new HiddenAttributesImpl( attributes, HiddenBranchRefFilter.isHidden( branchName ) );
    }

    public enum PathType {
        NOT_FOUND, DIRECTORY, FILE
    }

    public static SimplePathInfo getSimplePathInfo( final Git git,
                                                    final String branchName,
                                                    final String path ) {
        checkNotNull( "git", git );
        checkNotNull( "path", path );
        checkNotEmpty( "branchName", branchName );

        return retryIfNeeded( RuntimeException.class, () -> new GetPathInfo( git, branchName, path ).execute() );
    }

    public static PathInfo resolvePath( final Git git,
                                        final String branchName,
                                        final String path ) {
        checkNotNull( "git", git );
        checkNotNull( "path", path );
        checkNotEmpty( "branchName", branchName );

        final String gitPath = normalizePath( path );

        if ( gitPath.isEmpty() ) {
            return new PathInfo( null, "/", TREE );
        }

        return retryIfNeeded( RuntimeException.class, () -> {
            try ( final TreeWalk tw = new TreeWalk( git.getRepository() ) ) {
                final ObjectId tree = getTreeRefObjectId( git.getRepository(), branchName );
                tw.setFilter( PathFilter.create( gitPath ) );
                tw.reset( tree );
                while ( tw.next() ) {
                    if ( tw.getPathString().equals( gitPath ) ) {
                        if ( tw.getFileMode( 0 ).equals( TREE ) ) {
                            return new PathInfo( tw.getObjectId( 0 ), tw.getPathString(), TREE );
                        } else if ( tw.getFileMode( 0 ).equals( REGULAR_FILE ) || tw.getFileMode( 0 ).equals( EXECUTABLE_FILE ) ) {
                            final long size = tw.getObjectReader().getObjectSize( tw.getObjectId( 0 ), OBJ_BLOB );
                            return new PathInfo( tw.getObjectId( 0 ), tw.getPathString(), REGULAR_FILE, size );
                        }
                    }
                    if ( tw.isSubtree() ) {
                        tw.enterSubtree();
                    }
                }
                return null;
            } catch ( final Throwable ex ) {
                throw ex;
            }
        } );
    }

    public static List<PathInfo> listPathContent( final Git git,
                                                  final String branchName,
                                                  final String path ) {
        checkNotNull( "git", git );
        checkNotNull( "path", path );
        checkNotEmpty( "branchName", branchName );

        final String gitPath = normalizePath( path );

        return retryIfNeeded( RuntimeException.class, () -> {
            final List<PathInfo> result = new ArrayList<>();
            final ObjectId tree = getTreeRefObjectId( git.getRepository(), branchName );
            if ( tree == null ) {
                return result;
            }
            try ( final TreeWalk tw = new TreeWalk( git.getRepository() ) ) {
                boolean found = false;
                if ( gitPath.isEmpty() ) {
                    found = true;
                } else {
                    tw.setFilter( PathFilter.create( gitPath ) );
                }
                tw.reset( tree );
                while ( tw.next() ) {
                    if ( !found && tw.isSubtree() ) {
                        tw.enterSubtree();
                    }
                    if ( tw.getPathString().equals( gitPath ) ) {
                        found = true;
                        continue;
                    }
                    if ( found ) {
                        result.add( new PathInfo( tw.getObjectId( 0 ), tw.getPathString(), tw.getFileMode( 0 ) ) );
                    }
                }
                return result;
            } catch ( final Throwable ex ) {
                throw ex;
            }
        } );
    }

}
