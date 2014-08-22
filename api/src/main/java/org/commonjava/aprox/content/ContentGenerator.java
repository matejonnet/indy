package org.commonjava.aprox.content;

import java.util.List;

import org.commonjava.aprox.AproxWorkflowException;
import org.commonjava.aprox.model.ArtifactStore;
import org.commonjava.aprox.model.Group;
import org.commonjava.maven.galley.model.Transfer;

/**
 * Interface to support dynamic content generation. This was originally intended for generating metadata files when they aren't present on 
 * remote repositories. However, it's designed to accommodate all sorts of dynamic content.
 */
public interface ContentGenerator
{

    /**
     * Generate dynamic content in the event it's not found in the ArtifactStore. This is secondary to the main content retrieval logic, as a 
     * last effort to avoid returning a missing result.
     */
    Transfer generateFileContent( ArtifactStore store, String path )
        throws AproxWorkflowException;

    /**
     * Generate resources for any missing files that this generator can create. This is meant to contribute to an existing directory listing, so
     * the existing resources are given in order to allow the generator to determine whether a new resources is warranted.
     */
    List<StoreResource> generateDirectoryContent( ArtifactStore store, String path, List<StoreResource> existing )
        throws AproxWorkflowException;

    /**
     * Generate dynamic content for a group. This is the PRIMARY form of group access, with secondary action being to attempt normal retrieval from
     * one of the member stores.
     */
    Transfer generateGroupFileContent( Group group, List<ArtifactStore> members, String path )
        throws AproxWorkflowException;

    /**
     * Generate resources for merged group files that this generator can create.
     */
    List<StoreResource> generateGroupDirectoryContent( Group group, List<ArtifactStore> members, String path )
        throws AproxWorkflowException;

    /**
     * Tidy up any generated content associated with the stored file
     */
    void handleContentStorage( ArtifactStore store, String path, Transfer result )
        throws AproxWorkflowException;

    /**
     * Tidy up any generated content associated with the deleted file
     */
    void handleContentDeletion( ArtifactStore store, String path )
        throws AproxWorkflowException;

}
