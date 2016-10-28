/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.java.nio.IOException;
import org.uberfire.java.nio.base.FileSystemId;
import org.uberfire.java.nio.base.FileSystemState;
import org.uberfire.java.nio.base.FileSystemStateAware;
import org.uberfire.java.nio.base.options.CommentedOption;
import org.uberfire.java.nio.file.ClosedWatchServiceException;
import org.uberfire.java.nio.file.FileStore;
import org.uberfire.java.nio.file.FileSystem;
import org.uberfire.java.nio.file.InterruptedException;
import org.uberfire.java.nio.file.InvalidPathException;
import org.uberfire.java.nio.file.Path;
import org.uberfire.java.nio.file.PathMatcher;
import org.uberfire.java.nio.file.PatternSyntaxException;
import org.uberfire.java.nio.file.WatchEvent;
import org.uberfire.java.nio.file.WatchKey;
import org.uberfire.java.nio.file.WatchService;
import org.uberfire.java.nio.file.Watchable;
import org.uberfire.java.nio.file.attribute.UserPrincipalLookupService;
import org.uberfire.java.nio.file.spi.FileSystemProvider;
import org.uberfire.java.nio.fs.jgit.util.Git;
import org.uberfire.java.nio.fs.jgit.util.model.CommitInfo;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.eclipse.jgit.lib.Repository.*;
import static org.uberfire.commons.validation.PortablePreconditions.*;

public class JGitFileSystem implements FileSystem,
                                       FileSystemId,
                                       FileSystemStateAware {

    private static final Logger LOGGER = LoggerFactory.getLogger( JGitFileSystem.class );

    private static final Set<String> SUPPORTED_ATTR_VIEWS = unmodifiableSet( new HashSet<String>( asList( "basic", "version" ) ) );

    private final JGitFileSystemProvider provider;
    private final Git git;
    private final String toStringContent;
    private boolean isClosed = false;
    private final FileStore fileStore;
    private final String name;
    private final CredentialsProvider credential;
    private final Map<WatchService, Queue<WatchKey>> events = new ConcurrentHashMap<WatchService, Queue<WatchKey>>();
    private final Collection<WatchService> watchServices = new ArrayList<WatchService>();
    private final AtomicInteger numberOfCommitsSinceLastGC = new AtomicInteger( 0 );

    private FileSystemState state = FileSystemState.NORMAL;
    private CommitInfo batchCommitInfo = null;
    private Map<Path, Boolean> hadCommitOnBatchState = new ConcurrentHashMap<Path, Boolean>();

    private final Lock lock = new Lock();

    JGitFileSystem( final JGitFileSystemProvider provider,
                    final Map<String, String> fullHostNames,
                    final Git git,
                    final String name,
                    final CredentialsProvider credential ) {
        this.provider = checkNotNull( "provider", provider );
        this.git = checkNotNull( "git", git );
        this.name = checkNotEmpty( "name", name );
        this.credential = checkNotNull( "credential", credential );
        this.fileStore = new JGitFileStore( this.git.getRepository() );
        if ( fullHostNames != null && !fullHostNames.isEmpty() ) {
            final StringBuilder sb = new StringBuilder();
            final Iterator<Map.Entry<String, String>> iterator = fullHostNames.entrySet().iterator();
            while ( iterator.hasNext() ) {
                final Map.Entry<String, String> entry = iterator.next();
                sb.append( entry.getKey() ).append( "://" ).append( entry.getValue() ).append( "/" ).append( name );
                if ( iterator.hasNext() ) {
                    sb.append( "\n" );
                }
            }
            toStringContent = sb.toString();
        } else {
            toStringContent = "git://" + name;
        }
    }

    @Override
    public String id() {
        return name;
    }

    public String getName() {
        return name;
    }

    public Git getGit() {
        return git;
    }

    public CredentialsProvider getCredential() {
        return credential;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public boolean isOpen() {
        return !isClosed;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        checkClosed();
        return () -> new Iterator<Path>() {

            Iterator<Ref> branches = null;

            @Override
            public boolean hasNext() {
                if ( branches == null ) {
                    init();
                }
                return branches.hasNext();
            }

            private void init() {
                branches = git.listRefs().iterator();
            }

            @Override
            public Path next() {

                if ( branches == null ) {
                    init();
                }
                try {
                    return JGitPathImpl.createRoot( JGitFileSystem.this, "/",
                                                    shortenRefName( branches.next().getName() ) + "@" + name,
                                                    false );
                } catch ( NoSuchElementException e ) {
                    throw new IllegalStateException(
                            "The gitnio directory is in an invalid state. " +
                                    "If you are an IntelliJ IDEA user, " +
                                    "there is a known bug which requires specifying " +
                                    "a custom directory for your git repository. " +
                                    "You can specify a custom directory using '-Dorg.uberfire.nio.git.dir=/tmp/dir'. " +
                                    "For more details please see https://issues.jboss.org/browse/UF-275.", e );
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        checkClosed();
        return () -> new Iterator<FileStore>() {

            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < 1;
            }

            @Override
            public FileStore next() {
                if ( i < 1 ) {
                    i++;
                    return fileStore;
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        checkClosed();
        return SUPPORTED_ATTR_VIEWS;
    }

    @Override
    public Path getPath( final String first,
                         final String... more )
            throws InvalidPathException {
        checkClosed();
        if ( first == null || first.trim().isEmpty() ) {
            return new JGitFSPath( this );
        }

        if ( more == null || more.length == 0 ) {
            return JGitPathImpl.create( this, first, JGitPathImpl.DEFAULT_REF_TREE + "@" + name, false );
        }

        final StringBuilder sb = new StringBuilder();
        for ( final String segment : more ) {
            if ( segment.length() > 0 ) {
                if ( sb.length() > 0 ) {
                    sb.append( getSeparator() );
                }
                sb.append( segment );
            }
        }
        return JGitPathImpl.create( this, sb.toString(), first + "@" + name, false );
    }

    @Override
    public PathMatcher getPathMatcher( final String syntaxAndPattern )
            throws IllegalArgumentException, PatternSyntaxException, UnsupportedOperationException {
        checkClosed();
        checkNotEmpty( "syntaxAndPattern", syntaxAndPattern );
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService()
            throws UnsupportedOperationException {
        checkClosed();
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService()
            throws UnsupportedOperationException, IOException {
        checkClosed();
        final WatchService ws = new WatchService() {
            private boolean wsClose = false;

            @Override
            public WatchKey poll() throws ClosedWatchServiceException {
                return events.get( this ).poll();
            }

            @Override
            public WatchKey poll( long timeout,
                                  TimeUnit unit ) throws ClosedWatchServiceException, org.uberfire.java.nio.file.InterruptedException {
                return events.get( this ).poll();
            }

            @Override
            public synchronized WatchKey take() throws ClosedWatchServiceException, InterruptedException {
                while ( true ) {
                    if ( wsClose || isClosed ) {
                        throw new ClosedWatchServiceException( "This service is closed." );
                    } else if ( events.get( this ).size() > 0 ) {
                        return events.get( this ).poll();
                    } else {
                        try {
                            this.wait();
                        } catch ( final java.lang.InterruptedException e ) {
                        }
                    }
                }
            }

            @Override
            public boolean isClose() {
                return isClosed;
            }

            @Override
            public synchronized void close() throws IOException {
                wsClose = true;
                notifyAll();
                watchServices.remove( this );
            }

            @Override
            public String toString() {
                return "WatchService{" +
                        "FileSystem=" + JGitFileSystem.this.toString() +
                        '}';
            }
        };
        events.put( ws, new ConcurrentLinkedQueue<>() );
        watchServices.add( ws );
        return ws;
    }

    @Override
    public void close() throws IOException {
        if ( isClosed ) {
            return;
        }
        git.getRepository().close();
        isClosed = true;
        try {

            for ( final WatchService ws : new ArrayList<>( watchServices ) ) {
                try {
                    ws.close();
                } catch ( final Exception ex ) {
                    LOGGER.error( "Can't close watch service [" + toString() + "]", ex );
                }
            }
            watchServices.clear();
            events.clear();
        } catch ( final Exception ex ) {
            LOGGER.error( "Error during close of WatchServices [" + toString() + "]", ex );
        } finally {
            provider.onCloseFileSystem( this );
        }
    }

    private void checkClosed() throws IllegalStateException {
        if ( isClosed ) {
            throw new IllegalStateException( "FileSystem is closed." );
        }
    }

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }

        JGitFileSystem that = (JGitFileSystem) o;

        if ( fileStore != null ? !fileStore.equals( that.fileStore ) : that.fileStore != null ) {
            return false;
        }
        if ( !git.equals( that.git ) ) {
            return false;
        }
        if ( !name.equals( that.name ) ) {
            return false;
        }
        if ( !provider.equals( that.provider ) ) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return toStringContent;
    }

    @Override
    public int hashCode() {
        int result = provider.hashCode();
        result = 31 * result + git.hashCode();
        result = 31 * result + ( fileStore != null ? fileStore.hashCode() : 0 );
        result = 31 * result + name.hashCode();
        return result;
    }

    public void publishEvents( final Path watchable,
                               final List<WatchEvent<?>> elist ) {
        if ( this.events.isEmpty() ) {
            return;
        }

        final WatchKey wk = new WatchKey() {

            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public List<WatchEvent<?>> pollEvents() {
                return new ArrayList<WatchEvent<?>>( elist );
            }

            @Override
            public boolean reset() {
                return isOpen();
            }

            @Override
            public void cancel() {
            }

            @Override
            public Watchable watchable() {
                return watchable;
            }
        };

        for ( final Map.Entry<WatchService, Queue<WatchKey>> watchServiceQueueEntry : events.entrySet() ) {
            watchServiceQueueEntry.getValue().add( wk );
            final WatchService ws = watchServiceQueueEntry.getKey();
            synchronized ( ws ) {
                ws.notifyAll();
            }
        }
    }

    @Override
    public void dispose() {
        if ( !isClosed ) {
            close();
        }
        provider.onDisposeFileSystem( this );
    }

    public boolean isOnBatch() {
        return state.equals( FileSystemState.BATCH );
    }

    public void setState( String state ) {
        try {
            this.state = FileSystemState.valueOf( state );
        } catch ( final Exception ex ) {
            this.state = FileSystemState.NORMAL;
        }
    }

    private CommitInfo buildCommitInfo( final String defaultMessage,
                                        final CommentedOption op ) {
        String sessionId = null;
        String name = null;
        String email = null;
        String message = defaultMessage;
        TimeZone timeZone = null;
        Date when = null;

        if ( op != null ) {
            sessionId = op.getSessionId();
            name = op.getName();
            email = op.getEmail();
            if ( op.getMessage() != null && !op.getMessage().trim().isEmpty() ) {
                message = op.getMessage();
            }
            timeZone = op.getTimeZone();
            when = op.getWhen();
        }

        return new CommitInfo( sessionId, name, email, message, timeZone, when );
    }

    public void setBatchCommitInfo( final String defaultMessage,
                                    final CommentedOption op ) {
        this.batchCommitInfo = buildCommitInfo( defaultMessage, op );
    }

    public void setHadCommitOnBatchState( final Path path,
                                          final boolean hadCommitOnBatchState ) {
        final Path root = checkNotNull( "path", path ).getRoot();
        this.hadCommitOnBatchState.put( root.getRoot(), hadCommitOnBatchState );
    }

    public void setHadCommitOnBatchState( final boolean value ) {
        for ( Map.Entry<Path, Boolean> entry : hadCommitOnBatchState.entrySet() ) {
            entry.setValue( value );
        }
    }

    public boolean isHadCommitOnBatchState( final Path path ) {
        final Path root = checkNotNull( "path", path ).getRoot();
        return hadCommitOnBatchState.containsKey( root ) ? hadCommitOnBatchState.get( root ) : false;
    }

    public void setBatchCommitInfo( CommitInfo batchCommitInfo ) {
        this.batchCommitInfo = batchCommitInfo;
    }

    public CommitInfo getBatchCommitInfo() {
        return batchCommitInfo;
    }

    public int incrementAndGetCommitCount() {
        return numberOfCommitsSinceLastGC.incrementAndGet();
    }

    public void resetCommitCount() {
        numberOfCommitsSinceLastGC.set( 0 );
    }

    int getNumberOfCommitsSinceLastGC() {
        return numberOfCommitsSinceLastGC.get();
    }

    @Override
    public FileSystemState getState() {
        return state;
    }

    public void lock() {
        try {
            lock.lock();
        } catch ( java.lang.InterruptedException e ) {
        }
    }

    public void unlock() {
        lock.unlock();
    }

    private static class Lock {

        private final AtomicBoolean isLocked = new AtomicBoolean( false );

        public synchronized void lock() throws java.lang.InterruptedException {
            while ( !isLocked.compareAndSet( false, true ) ) {
                wait();
            }
        }

        public synchronized void unlock() {
            isLocked.set( false );
            notifyAll();
        }
    }

}
