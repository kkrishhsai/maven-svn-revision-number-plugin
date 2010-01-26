/*-
 * Copyright (c) 2009-2010, Oleg Estekhin
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  * Neither the names of the copyright holders nor the names of their
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */

package oe.maven.plugins.revision;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * Retrieves the revision number and the status of the Subversion working copy directory.
 *
 * @goal revision
 * @phase initialize
 * @requiresProject
 */
public class RevisionMojo extends AbstractMojo {

    static {
        DAVRepositoryFactory.setup(); // http, https
        SVNRepositoryFactoryImpl.setup(); // svn, svn+xxx
        FSRepositoryFactory.setup(); // file
    }


    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject project;


    /**
     * The Subversion working copy directory.
     * The plugin will evaluate the aggregated status and revision number of this directory and its contents.
     *
     * @parameter expression="${workingCopyDirectory}" default-value="${basedir}"
     * @required
     */
    private File workingCopyDirectory;


    /**
     * Whether to report the mixed revisions information. If set to {@code false} then only the maximum revision number
     * will be reported.
     *
     * @parameter expression="${reportMixedRevisions}" default-value="true"
     */
    private boolean reportMixedRevisions;

    /**
     * Whether to report the status information. If set to {@code false} then only the revision number will be
     * reported.
     *
     * @parameter expression="${reportStatus}" default-value="true"
     */
    private boolean reportStatus;

    /**
     * Whether to collect the status information on items that are not under version control.
     *
     * @parameter expression="${reportUnversioned}" default-value="true"
     */
    private boolean reportUnversioned;

    /**
     * Whether to collect the status information on items that were set to be ignored.
     *
     * @parameter expression="${reportIgnored}" default-value="false"
     */
    private boolean reportIgnored;

    /**
     * Whether to check the remote repository and report if the local items are out-of-date.
     *
     * @parameter expression="${reportOutOfDate}" default-value="false"
     */
    private boolean reportOutOfDate;


    /**
     * Provides detailed messages while this goal is running.
     *
     * @parameter expression="${verbose}" default-value="false"
     */
    private boolean verbose;


    public void execute() throws MojoExecutionException, MojoFailureException {
        if ( verbose ) {
            getLog().info( "${workingCopyDirectory}: " + workingCopyDirectory );
            getLog().info( "report mixed revisions: " + reportMixedRevisions );
            getLog().info( "report status: " + reportStatus );
            getLog().info( "report unversioned: " + reportUnversioned );
            getLog().info( "report ignored: " + reportIgnored );
            getLog().info( "report out-of-date: " + reportOutOfDate );
        }
        try {
            String repository;
            String path;
            String revision;
            String fileNameSafeRevision;
            if ( SVNWCUtil.isVersionedDirectory( workingCopyDirectory ) ) {
                SVNClientManager clientManager = SVNClientManager.newInstance();
                SVNStatusClient statusClient = clientManager.getStatusClient();

                SVNEntry entry = statusClient.doStatus( workingCopyDirectory, false ).getEntry();
                repository = entry.getRepositoryRoot();
                path = entry.getURL().substring( repository.length() );
                if ( path.startsWith( "/" ) ) {
                    path = path.substring( 1 );
                }

                StatusCollector statusCollector = new StatusCollector();
                statusClient.doStatus( workingCopyDirectory,
                        SVNRevision.HEAD, SVNDepth.INFINITY,
                        reportOutOfDate, true, reportIgnored, false,
                        statusCollector,
                        null );
                revision = statusCollector.getStatus( StatusCharacters.STANDARD );
                fileNameSafeRevision = statusCollector.getStatus( StatusCharacters.FILE_NAME_SAFE );
            } else {
                repository = "";
                path = "";
                revision = "unversioned";
                fileNameSafeRevision = "unversioned";
            }
            project.getProperties().setProperty( "workingCopyDirectory.repository", repository );
            project.getProperties().setProperty( "workingCopyDirectory.path", path );
            project.getProperties().setProperty( "workingCopyDirectory.revision", revision );
            project.getProperties().setProperty( "workingCopyDirectory.fileNameSafeRevision", fileNameSafeRevision );
            if ( verbose ) {
                getLog().info( "${workingCopyDirectory.repository} is set to \"" + repository + '\"' );
                getLog().info( "${workingCopyDirectory.path} is set to \"" + path + '\"' );
                getLog().info( "${workingCopyDirectory.revision} is set to \"" + revision + '\"' );
                getLog().info( "${workingCopyDirectory.fileNameSafeRevision} is set to \"" + fileNameSafeRevision + '\"' );
            }
        } catch ( SVNException e ) {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }


    private final class StatusCollector implements ISVNStatusHandler {

        private long maximumRevisionNumber;

        private long minimumRevisionNumber;

        private Set<SVNStatusType> localStatusTypes;

        private boolean remoteChanges;

        private StatusCollector() {
            maximumRevisionNumber = Long.MIN_VALUE;
            minimumRevisionNumber = Long.MAX_VALUE;
            localStatusTypes = new HashSet<SVNStatusType>();
        }

        public void handleStatus( SVNStatus status ) {
            SVNStatusType contentsStatusType = status.getContentsStatus();
            localStatusTypes.add( contentsStatusType );
            long revisionNumber = status.getRevision().getNumber();
            if ( revisionNumber >= 0L ) {
                maximumRevisionNumber = Math.max( maximumRevisionNumber, revisionNumber );
            }
            if ( revisionNumber > 0L ) {
                minimumRevisionNumber = Math.min( minimumRevisionNumber, revisionNumber );
            }
            SVNStatusType propertiesStatusType = status.getPropertiesStatus();
            localStatusTypes.add( propertiesStatusType );
            boolean remoteStatusTypes = !SVNStatusType.STATUS_NONE.equals( status.getRemotePropertiesStatus() )
                    || !SVNStatusType.STATUS_NONE.equals( status.getRemoteContentsStatus() );
            remoteChanges = remoteChanges || remoteStatusTypes;
            if ( verbose ) {
                StringBuilder buffer = new StringBuilder();
                buffer.append( status.getContentsStatus().getCode() ).append( status.getPropertiesStatus().getCode() );
                buffer.append( remoteStatusTypes ? '*' : ' ' );
                buffer.append( ' ' ).append( String.format( "%6d", status.getRevision().getNumber() ) );
                buffer.append( ' ' ).append( status.getFile() );
                getLog().info( buffer.toString() );
            }
        }

        public String getStatus( StatusCharacters statusCharacters ) {
            Set<SVNStatusType> tempStatusTypes = new HashSet<SVNStatusType>( localStatusTypes );
            StringBuilder result = new StringBuilder();
            if ( maximumRevisionNumber != Long.MIN_VALUE ) {
                result.append( 'r' ).append( maximumRevisionNumber );
                if ( minimumRevisionNumber != maximumRevisionNumber && reportMixedRevisions ) {
                    result.append( '-' ).append( 'r' ).append( minimumRevisionNumber );
                }
            }
            if ( reportStatus ) {
                tempStatusTypes.remove( SVNStatusType.STATUS_NONE );
                tempStatusTypes.remove( SVNStatusType.STATUS_NORMAL );
                if ( !tempStatusTypes.isEmpty() ) {
                    result.append( statusCharacters.separator );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_MODIFIED ) ) {
                    result.append( statusCharacters.modified );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_ADDED ) ) {
                    result.append( statusCharacters.added );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_DELETED ) ) {
                    result.append( statusCharacters.deleted );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_UNVERSIONED ) && reportUnversioned ) {
                    result.append( statusCharacters.unversioned );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_MISSING ) ) {
                    result.append( statusCharacters.missing );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_REPLACED ) ) {
                    result.append( statusCharacters.replaced );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_CONFLICTED ) ) {
                    result.append( statusCharacters.conflicted );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_OBSTRUCTED ) ) {
                    result.append( statusCharacters.obstructed );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_IGNORED ) && reportIgnored ) {
                    result.append( statusCharacters.ignored );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_INCOMPLETE ) ) {
                    result.append( statusCharacters.incomplete );
                }
                if ( tempStatusTypes.remove( SVNStatusType.STATUS_EXTERNAL ) ) {
                    result.append( statusCharacters.external );
                }
                if ( !tempStatusTypes.isEmpty() ) {
                    getLog().warn( "unprocessed svn statuses: " + tempStatusTypes );
                }
                if ( remoteChanges && reportOutOfDate ) {
                    result.append( statusCharacters.outOfDate );
                }
            }
            return result.toString();
        }

    }

    private static final class StatusCharacters {

        public static final StatusCharacters STANDARD = new StatusCharacters(
                ' ',
                SVNStatusType.STATUS_MODIFIED.getCode(),
                SVNStatusType.STATUS_ADDED.getCode(),
                SVNStatusType.STATUS_DELETED.getCode(),
                SVNStatusType.STATUS_UNVERSIONED.getCode(),
                SVNStatusType.STATUS_MISSING.getCode(),
                SVNStatusType.STATUS_REPLACED.getCode(),
                SVNStatusType.STATUS_CONFLICTED.getCode(),
                SVNStatusType.STATUS_OBSTRUCTED.getCode(),
                SVNStatusType.STATUS_IGNORED.getCode(),
                SVNStatusType.STATUS_INCOMPLETE.getCode(),
                SVNStatusType.STATUS_EXTERNAL.getCode(),
                '*'
        );

        public static final StatusCharacters FILE_NAME_SAFE = new StatusCharacters(
                '-',
                SVNStatusType.STATUS_MODIFIED.getCode(),
                SVNStatusType.STATUS_ADDED.getCode(),
                SVNStatusType.STATUS_DELETED.getCode(),
                'u', // SVNStatusType.STATUS_UNVERSIONED.getCode(),
                'm', // SVNStatusType.STATUS_MISSING.getCode(),
                SVNStatusType.STATUS_REPLACED.getCode(),
                SVNStatusType.STATUS_CONFLICTED.getCode(),
                'o', // SVNStatusType.STATUS_OBSTRUCTED.getCode(),
                SVNStatusType.STATUS_IGNORED.getCode(),
                'i', // SVNStatusType.STATUS_INCOMPLETE.getCode(),
                SVNStatusType.STATUS_EXTERNAL.getCode(),
                'd'
        );


        public final char separator;

        public final char modified;

        public final char added;

        public final char deleted;

        public final char unversioned;

        public final char missing;

        public final char replaced;

        public final char conflicted;

        public final char obstructed;

        public final char ignored;

        public final char incomplete;

        public final char external;

        public final char outOfDate;


        private StatusCharacters( char separator, char modified, char added, char deleted, char unversioned, char missing, char replaced, char conflicted, char obstructed, char ignored, char incomplete, char external, char outOfDate ) {
            this.separator = separator;
            this.modified = modified;
            this.added = added;
            this.deleted = deleted;
            this.unversioned = unversioned;
            this.missing = missing;
            this.replaced = replaced;
            this.conflicted = conflicted;
            this.obstructed = obstructed;
            this.ignored = ignored;
            this.incomplete = incomplete;
            this.external = external;
            this.outOfDate = outOfDate;
        }

    }

}
