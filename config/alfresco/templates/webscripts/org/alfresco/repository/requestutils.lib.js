/*
 * Site related utility functions
 */

/**
 * Returns the node as specified by the given arguments.
 *
 * @param siteId the site for which a node is requested
 * @param containerId the component to look in for the node.
 * @param path a path to the node. Returns the root node in case path is null or ''
 *        return null in case no node can be found for the given path
 * @return the node or a json error in case the node could not be fetched. Check with .
 */
function findNodeInSite()
{
   var siteId = url.templateArgs.site;
   var containerId = url.templateArgs.container;
   var path = (url.templateArgs.path != undefined) ? url.templateArgs.path : "";

   // fetch site
   var site = siteService.getSite(siteId);
   if (site === null)
   {
      status.setCode(status.STATUS_NOT_FOUND, "Site " + siteId + " does not exist");
      return null;
   }

   // fetch container
   var node = site.getContainer(containerId);
   if (node === null)
   {
      status.setCode(status.STATUS_NOT_FOUND, "The container " + containerId + "could not be found in site " + siteId + ". (No write permission?)");
      return null;
   }
   
   // try to fetch the the path is there is any
   if ((path !== null) && (path.length > 0))
   {
      node = node.childByNamePath(path);
      if (node === null)
      {
         status.setCode(status.STATUS_NOT_FOUND, "No node found for the given path: \"" + path + "\" in container " + containerId + " of site " + siteId);
         return null;
      }
   }

   return node;
}

function findFromReference()
{
   var nodeRef = url.templateArgs.store_type + "://" + url.templateArgs.store_id + "/" + url.templateArgs.id;
   var node = search.findNode(nodeRef);
   if (node === null)
   { 
      status.setCode(status.STATUS_NOT_FOUND, "Node " + nodeRef + " does not exist");
   }
   return node;
}

/**
 * Returns the node referenced by the request url .
 * Currently two different types of urls are supported (site/container or nodeRef),
 * this method takes care to hide those differences from the rest of the code.
 *
 * @return the node or null & error status set if not found.
 */
function getRequestNode()
{
   // check whether we got a node reference or a site related uri
   var node = null;
   if (url.templateArgs.store_type != undefined)
   {
       node = findFromReference();
   }
   // site related uri
   else if (url.templateArgs.site != undefined)
   {
      node = findNodeInSite();
   }
   else
   {
      // unknown request params
      status.setCode(status.STATUS_INTERNAL_SERVER_ERROR, "Unknown request parameters (webscript incorrectly configured?)");
   }
   return node;
}
