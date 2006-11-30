/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.web.bean.wcm;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.DataModel;
import javax.faces.model.ListDataModel;
import javax.faces.model.SelectItem;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.model.WCMAppModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.QName;
import org.alfresco.web.app.Application;
import org.alfresco.web.bean.FileUploadBean;
import org.alfresco.web.bean.wizard.BaseWizardBean;
import org.alfresco.web.data.IDataContainer;
import org.alfresco.web.data.QuickSort;
import org.alfresco.web.forms.*;
import org.alfresco.web.forms.xforms.SchemaFormBuilder;
import org.alfresco.web.ui.common.component.UIListItem;
import org.alfresco.web.ui.common.Utils;
import org.alfresco.web.ui.wcm.WebResources;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.xs.*;
import org.w3c.dom.Document;

/**
 * Bean implementation for the "Create XML Form" dialog
 * 
 * @author arielb
 */
public class CreateFormWizard 
   extends BaseWizardBean
{

   /////////////////////////////////////////////////////////////////////////////

   private static final String NO_DEFAULT_WORKFLOW_SELECTED = "no_default_workflow_selected";

   /**
    * Simple wrapper class to represent a form data renderer
    */
   public class RenderingEngineTemplateData
   {
      private final String fileName;
      private final File file;
      private final String title;
      private final String description;
      private final String mimetypeForRendition;
      private final String outputPathPatternForRendition;
      private final RenderingEngine renderingEngine;

      public RenderingEngineTemplateData(final String fileName, 
                                         final File file,
                                         final String title,
                                         final String description,
                                         final String outputPathPatternForRendition,
                                         final String mimetypeForRendition,
                                         final RenderingEngine renderingEngine)
      {
         this.fileName = fileName;
         this.file = file;
         this.title = title;
         this.description = description;
         this.outputPathPatternForRendition = outputPathPatternForRendition;
         this.mimetypeForRendition = mimetypeForRendition;
         this.renderingEngine = renderingEngine;
      }
      
      public String getOutputPathPatternForRendition()
      {
         return this.outputPathPatternForRendition;
      }

      public String getMimetypeForRendition()
      {
         return this.mimetypeForRendition;
      }
      
      public String getFileName()
      {
         return this.fileName;
      }

      public File getFile()
      {
         return this.file;
      }

      public String getTitle()
      {
         return this.title;
      }

      public String getDescription()
      {
         return this.description;
      }

      public RenderingEngine getRenderingEngine()
      {
         return this.renderingEngine;
      }

      public String toString()
      {
         return (this.getClass().getName() + "{" +
                 "fileName: " + this.getFileName() + "," +
                 "mimetypeForRendition: " + this.getMimetypeForRendition() + "," +
                 "outputPathPatternForRendition: " + this.getOutputPathPatternForRendition() + "," +
                 "renderingEngine: " + this.getRenderingEngine().getName() + 
                 "}");
      }
   }

   /////////////////////////////////////////////////////////////////////////////
   
   public static final String FILE_RENDERING_ENGINE_TEMPLATE = 
      "rendering-engine-template";

   public static final String FILE_SCHEMA = "schema";

   private final static Log LOGGER = LogFactory.getLog(CreateFormWizard.class);
   
   private String schemaRootElementName = null;
   private String formName = null;
   private String formTitle = null;
   private String formDescription = null;
   private String renderingEngineTemplateTitle = null;
   private String renderingEngineTemplateDescription = null;
   private String defaultWorkflowName = null;
   private RenderingEngine renderingEngine = null;
   protected ContentService contentService;
   protected MimetypeService mimetypeService;
   protected WorkflowService workflowService;
   private DataModel renderingEngineTemplatesDataModel;
   private List<RenderingEngineTemplateData> renderingEngineTemplates = null;
   private String outputPathPatternForFormInstanceData = null;
   private String outputPathPatternForRendition = null;
   private String mimetypeForRendition = null;
   private List<SelectItem> mimetypeChoices = null;

   // ------------------------------------------------------------------------------
   // Wizard implementation
   
   @Override
   protected String finishImpl(final FacesContext context, final String outcome)
      throws Exception
   {
      LOGGER.debug("creating form " + this.getFormName());

      final FormsService ts = FormsService.getInstance();
      // get the node ref of the node that will contain the content
      final NodeRef contentFormsNodeRef = ts.getContentFormsNodeRef();

      final FileInfo folderInfo = 
         this.fileFolderService.create(contentFormsNodeRef,
                                       this.getFormName(),
                                       ContentModel.TYPE_FOLDER);
      FileInfo fileInfo = 
         this.fileFolderService.create(folderInfo.getNodeRef(),
                                       this.getSchemaFileName(),
                                       ContentModel.TYPE_CONTENT);
      // get a writer for the content and put the file
      ContentWriter writer = this.contentService.getWriter(fileInfo.getNodeRef(),
                                                           ContentModel.PROP_CONTENT,
                                                           true);
      // set the mimetype and encoding
      writer.setMimetype(MimetypeMap.MIMETYPE_XML);
      writer.setEncoding("UTF-8");
      writer.putContent(this.getSchemaFile());

      // apply the titled aspect - title and description
      Map<QName, Serializable> props = new HashMap<QName, Serializable>(2, 1.0f);
      props.put(ContentModel.PROP_TITLE, this.getFormTitle());
      props.put(ContentModel.PROP_DESCRIPTION, this.getFormDescription());
      this.nodeService.addAspect(folderInfo.getNodeRef(), ContentModel.ASPECT_TITLED, props);
      
      props = new HashMap<QName, Serializable>(3, 1.0f);
      props.put(WCMAppModel.PROP_XML_SCHEMA, fileInfo.getNodeRef());
      props.put(WCMAppModel.PROP_XML_SCHEMA_ROOT_ELEMENT_NAME, 
                this.getSchemaRootElementName());
      props.put(WCMAppModel.PROP_OUTPUT_PATH_PATTERN_FORM_INSTANCE_DATA, 
                this.getOutputPathPatternForFormInstanceData());
      if (this.defaultWorkflowName != null)
      {
         props.put(WCMAppModel.PROP_DEFAULT_WORKFLOW_NAME, this.defaultWorkflowName);
      }
      this.nodeService.addAspect(folderInfo.getNodeRef(), WCMAppModel.ASPECT_FORM, props);
         
      for (RenderingEngineTemplateData retd : this.renderingEngineTemplates)
      {
         LOGGER.debug("adding rendering engine template " + retd + 
                      " to form " + this.getFormName());

         NodeRef renderingEngineTemplateNodeRef = 
            this.fileFolderService.searchSimple(folderInfo.getNodeRef(), retd.getFileName());
         if (renderingEngineTemplateNodeRef == null)
         {
            try
            {
               fileInfo = this.fileFolderService.create(folderInfo.getNodeRef(),
                                                        retd.getFileName(),
                                                        ContentModel.TYPE_CONTENT);
               if (LOGGER.isDebugEnabled())
                  LOGGER.debug("Created file node for file: " + retd.getFileName());
               renderingEngineTemplateNodeRef = fileInfo.getNodeRef();            
            }
            catch (final FileExistsException fee)
            {
               LOGGER.error(fee.getName() + " already exists in " + 
                            fee.getParentNodeRef());
               throw fee;
            }

            // get a writer for the content and put the file
            writer = this.contentService.getWriter(renderingEngineTemplateNodeRef, 
                                                   ContentModel.PROP_CONTENT, 
                                                   true);
            // set the mimetype and encoding
            // XXXarielb mime type of template isn't known
            // writer.setMimetype("text/xml");
            writer.setEncoding("UTF-8");
            writer.putContent(retd.getFile());

            this.nodeService.createAssociation(folderInfo.getNodeRef(),
                                               renderingEngineTemplateNodeRef,
                                               WCMAppModel.ASSOC_RENDERING_ENGINE_TEMPLATES);
            props = new HashMap<QName, Serializable>(2, 1.0f);
            props.put(WCMAppModel.PROP_PARENT_RENDERING_ENGINE_NAME, 
                      retd.getRenderingEngine().getName());
            props.put(WCMAppModel.PROP_FORM_SOURCE, folderInfo.getNodeRef());
            this.nodeService.addAspect(renderingEngineTemplateNodeRef, 
                                       WCMAppModel.ASPECT_RENDERING_ENGINE_TEMPLATE, 
                                       props);

            // apply the titled aspect - title and description
            props = new HashMap<QName, Serializable>(2, 1.0f);
            props.put(ContentModel.PROP_TITLE, retd.getTitle());
            props.put(ContentModel.PROP_DESCRIPTION, retd.getDescription());
            this.nodeService.addAspect(renderingEngineTemplateNodeRef, 
                                       ContentModel.ASPECT_TITLED, 
                                       props);
         }

         LOGGER.debug("adding rendition properties to " + renderingEngineTemplateNodeRef);
         props = new HashMap<QName, Serializable>(2, 1.0f);
         props.put(WCMAppModel.PROP_OUTPUT_PATH_PATTERN_RENDITION, 
                   retd.getOutputPathPatternForRendition());
         props.put(WCMAppModel.PROP_MIMETYPE_FOR_RENDITION, 
                   retd.getMimetypeForRendition());
         this.nodeService.createNode(renderingEngineTemplateNodeRef,
                                     WCMAppModel.ASSOC_RENDITION_PROPERTIES,
                                     WCMAppModel.ASSOC_RENDITION_PROPERTIES,
                                     WCMAppModel.TYPE_RENDITION_PROPERTIES,
                                     props);
      }
      // return the default outcome
      return outcome;
   }

   @Override
   public void init(Map<String, String> parameters)
   {
      super.init(parameters);
      
      this.removeUploadedSchemaFile();
      this.removeUploadedRenderingEngineTemplateFile();
      this.schemaRootElementName = null;
      this.formName = null;
      this.formTitle = null;
      this.formDescription = null;
      this.renderingEngineTemplateTitle = null;
      this.renderingEngineTemplateDescription = null; 
      this.renderingEngine = null;
      this.renderingEngineTemplates = new ArrayList<RenderingEngineTemplateData>();
      this.outputPathPatternForFormInstanceData = null;
      this.outputPathPatternForRendition = null;
      this.mimetypeForRendition = null;
      this.defaultWorkflowName = null;
   }
   
   @Override
   public String cancel()
   {
      this.removeUploadedSchemaFile();
      this.removeUploadedRenderingEngineTemplateFile();
      return super.cancel();
   }
   
   @Override
   public boolean getNextButtonDisabled()
   {
      // TODO: Allow the next button state to be configured so that
      //       wizard implementations don't have to worry about 
      //       checking step numbers
      
      boolean disabled = false;
      int step = Application.getWizardManager().getCurrentStep();
      switch(step)
      {
         case 1:
         {
            disabled = (this.getSchemaFileName() == null || 
                        this.getSchemaFileName().length() == 0);
            break;
         }
      }
      
      return disabled;
   }
   
   /**
    * @return true if the Add To List button on the configure rendering engines 
    * page should be disabled
    */
   public boolean getAddToListDisabled()
   {
      return this.getRenderingEngineTemplateFileName() == null;
   }

   /**
    * @return Returns the output path for the rendition.
    */
   public String getOutputPathPatternForRendition()
   {
      return (this.outputPathPatternForRendition == null
              ? "${name}.${extension}"
              : this.outputPathPatternForRendition);
   }

   /**
    * @param outputPathPatternForRendition The output path for the rendition.
    */
   public void setOutputPathPatternForRendition(final String outputPathPatternForRendition)
   {
      this.outputPathPatternForRendition = outputPathPatternForRendition;
   }

   /**
    * @return Returns the mimetype.
    */
   public String getMimetypeForRendition()
   {
      if (this.mimetypeForRendition == null && this.outputPathPatternForRendition != null)
      {
         this.mimetypeForRendition = 
            this.mimetypeService.guessMimetype(this.outputPathPatternForRendition);
      }
      return this.mimetypeForRendition;
   }

   /**
    * @param mimetype The mimetype to set.
    */
   public void setMimetypeForRendition(final String mimetypeForRendition)
   {
      this.mimetypeForRendition = mimetypeForRendition;
   }

   /**
    * Add the selected rendering engine to the list
    */
   public void addSelectedRenderingEngineTemplate(final ActionEvent event)
   {
      final RenderingEngineTemplateData data = 
         this.new RenderingEngineTemplateData(this.getRenderingEngineTemplateFileName(),
                                              this.getRenderingEngineTemplateFile(),
                                              this.getRenderingEngineTemplateTitle(),
                                              this.getRenderingEngineTemplateDescription(),
                                              this.getOutputPathPatternForRendition(),
                                              this.getMimetypeForRendition(),
                                              this.renderingEngine);
      this.renderingEngineTemplates.add(data);
      this.removeUploadedRenderingEngineTemplateFile();
      this.renderingEngine = null;
      this.outputPathPatternForRendition = null;
      this.mimetypeForRendition = null;
      this.renderingEngineTemplateTitle = null;
      this.renderingEngineTemplateDescription = null;
   }
   
   /**
    * Action handler called when the Remove button is pressed to remove a 
    * rendering engine
    */
   public void removeSelectedRenderingEngineTemplate(final ActionEvent event)
   {
      final RenderingEngineTemplateData wrapper = (RenderingEngineTemplateData)
         this.renderingEngineTemplatesDataModel.getRowData();
      if (wrapper != null)
      {
         this.renderingEngineTemplates.remove(wrapper);
      }
   }

   /**
    * Action handler called when the user changes the selected mimetype
    */
   public String mimetypeForRenditionChanged(final ValueChangeEvent vce)
   {
      // refresh the current page
      return null;
   }
   
   /**
    * Action handler called when the user wishes to remove an uploaded file
    */
   public String removeUploadedSchemaFile()
   {
      clearUpload(FILE_SCHEMA);
      
      // refresh the current page
      return null;
   }
   
   /**
    * Action handler called when the user wishes to remove an uploaded file
    */
   public String removeUploadedRenderingEngineTemplateFile()
   {
      clearUpload(FILE_RENDERING_ENGINE_TEMPLATE);
      
      // refresh the current page
      return null;
   }
   
   
   // ------------------------------------------------------------------------------
   // Bean Getters and Setters

   /**
    * Returns the properties for current configured output methods JSF DataModel
    * 
    * @return JSF DataModel representing the current configured output methods
    */
   public DataModel getRenderingEngineTemplatesDataModel()
   {
      if (this.renderingEngineTemplatesDataModel == null)
      {
         this.renderingEngineTemplatesDataModel = new ListDataModel();
      }
      
      this.renderingEngineTemplatesDataModel.setWrappedData(this.renderingEngineTemplates);
      
      return this.renderingEngineTemplatesDataModel;
   }

   /**
    * Returns all configured rendering engine templates.
    */
   public List<RenderingEngineTemplateData> getRenderingEngineTemplates()
   {
      return this.renderingEngineTemplates;
   }
   
   /**
    * @return Returns the mime type currenty selected
    */
   public String getRenderingEngineName()
   {
      if (this.renderingEngine == null &&
          this.getRenderingEngineTemplateFileName() != null)
      {
         final FormsService fs = FormsService.getInstance();
         this.renderingEngine = 
            fs.guessRenderingEngine(this.getRenderingEngineTemplateFileName());
      }
      return (this.renderingEngine == null
              ? null
              : this.renderingEngine.getName());
   }
   
   /**
    * @param renderingEngineName Sets the currently selected rendering engine name
    */
   public void setRenderingEngineName(final String renderingEngineName)
   {
      final FormsService fs = FormsService.getInstance();
      this.renderingEngine = (renderingEngineName == null
                              ? null
                              : fs.getRenderingEngine(renderingEngineName));
   }
   
   /**
    * @return Returns a list of mime types to allow the user to select from
    */
   public List<SelectItem> getRenderingEngineChoices()
   {
      final FormsService fs = FormsService.getInstance();
      final List<SelectItem>  result = new LinkedList<SelectItem>();
      for (RenderingEngine re : fs.getRenderingEngines())
      {
         result.add(new SelectItem(re.getName(), re.getName()));
      }
      return result;
   }
   
   /**
    * Returns a list of mime types in the system
    * 
    * @return List of mime types
    */
   public List<SelectItem> getMimeTypeChoices()
   {
       if (this.mimetypeChoices == null)
       {
           this.mimetypeChoices = new ArrayList<SelectItem>(50);
           
           final Map<String, String> mimetypes = this.mimetypeService.getDisplaysByMimetype();
           for (String mimetype : mimetypes.keySet())
           {
              this.mimetypeChoices.add(new SelectItem(mimetype, 
                                                      mimetypes.get(mimetype)));
           }
           
           // make sure the list is sorted by the values
           final QuickSort sorter = new QuickSort(this.mimetypeChoices, 
                                                  "label", 
                                                  true, 
                                                  IDataContainer.SORT_CASEINSENSITIVE);
           sorter.sort();
       }
       
       return this.mimetypeChoices;
   }

   private FileUploadBean getFileUploadBean(final String id)
   {
      final FacesContext ctx = FacesContext.getCurrentInstance();
      final Map sessionMap = ctx.getExternalContext().getSessionMap();
      return (FileUploadBean)sessionMap.get(FileUploadBean.getKey(id));
   }
   
   /**
    * @return Returns the name of the file
    */
   private String getFileName(final String id)
   {
      // try and retrieve the file and filename from the file upload bean
      // representing the file we previously uploaded.
      final FileUploadBean fileBean = this.getFileUploadBean(id);
      return fileBean == null ? null : fileBean.getFileName();
   }
   
   /**
    * @return Returns the schema file or <tt>null</tt>
    */
   private File getFile(final String id)
   {
      // try and retrieve the file and filename from the file upload bean
      // representing the file we previously uploaded.
      final FileUploadBean fileBean = this.getFileUploadBean(id);
      return fileBean != null ? fileBean.getFile() : null;
   }
   
   /**
    * @return Returns the schema file or <tt>null</tt>
    */
   public File getSchemaFile()
   {
      return this.getFile(FILE_SCHEMA);
   }
   
   /**
    * @return Returns the schema file or <tt>null</tt>
    */
   public String getSchemaFileName()
   {
      // try and retrieve the file and filename from the file upload bean
      // representing the file we previously uploaded.
      return this.getFileName(FILE_SCHEMA);
   }
   
   /**
    * @return Returns the schema file or <tt>null</tt>
    */
   public String getRenderingEngineTemplateFileName()
   {
      return this.getFileName(FILE_RENDERING_ENGINE_TEMPLATE);
   }
   
   /**
    * @return Returns the rendering engine file or <tt>null</tt>
    */
   public File getRenderingEngineTemplateFile()
   {
      return this.getFile(FILE_RENDERING_ENGINE_TEMPLATE);
   }

   /**
    * Sets the root element name to use when processing the schema.
    */
   public void setSchemaRootElementName(final String schemaRootElementName)
   {
      this.schemaRootElementName = schemaRootElementName;
   }

   /**
    * Returns the root element name to use when processing the schema.
    */
   public String getSchemaRootElementName()
   {
      return this.schemaRootElementName;
   }
   
   /**
    * @return the possible root element names for use with the schema based on 
    * the element declarations it defines.
    */
   public List<SelectItem> getSchemaRootElementNameChoices()
   {
      final List<SelectItem> result = new LinkedList<SelectItem>();
      if (this.getSchemaFile() != null)
      {
         final FormsService ts = FormsService.getInstance();
         try
         {
            final Document d = ts.parseXML(this.getSchemaFile());
            final XSModel xsm = SchemaFormBuilder.loadSchema(d);
            final XSNamedMap elementsMap = xsm.getComponents(XSConstants.ELEMENT_DECLARATION);
            for (int i = 0; i < elementsMap.getLength(); i++)
            {
               final XSElementDeclaration e = (XSElementDeclaration)elementsMap.item(i);
               result.add(new SelectItem(e.getName(), e.getName()));
            }
         }
         catch (Exception e)
         {
            final String msg = "unable to parse " + this.getSchemaFileName();
            this.removeUploadedSchemaFile();
            Utils.addErrorMessage(msg, e);
            throw new AlfrescoRuntimeException(msg, e);
         }
      }
      return result;
   }
   
   /**
    * Sets the human friendly name for this form.
    */
   public void setFormName(final String formName)
   {
      this.formName = formName;
   }

   /**
    * @return the human friendly name for this form.
    */
   public String getFormName()
   {
      return (this.formName == null && this.getSchemaFileName() != null
              ? this.getSchemaFileName().replaceAll("(.+)\\..*", "$1")
              : this.formName);
   }
   /**
    * @return Returns the output path for form instance data.
    */
   public String getOutputPathPatternForFormInstanceData()
   {
      if (this.outputPathPatternForFormInstanceData == null)
      {
         this.outputPathPatternForFormInstanceData = "${name}.xml";
      }
      return this.outputPathPatternForFormInstanceData;
   }

   /**
    * @param outputPathPatternForFormInstanceData the output path for form instance data
    */
   public void setOutputPathPatternForFormInstanceData(final String outputPathPatternForFormInstanceData)
   {
      this.outputPathPatternForFormInstanceData = outputPathPatternForFormInstanceData;
   }

   /**
    * Sets the title for this form.
    */
   public void setFormTitle(final String formTitle)
   {
      this.formTitle = formTitle;
   }

   /**
    * @return the title for this form.
    */
   public String getFormTitle()
   {
      return (this.formTitle == null && this.getSchemaFileName() != null
              ? this.getSchemaFileName().replaceAll("(.+)\\..*", "$1")
              : this.formTitle);
   }

   /**
    * Sets the description for this form.
    */
   public void setFormDescription(final String formDescription)
   {
      this.formDescription = formDescription;
   }

   /**
    * @return the description for this form.
    */
   public String getFormDescription()
   {
      return this.formDescription;
   }

   /**
    * Sets the title for this renderingEngineTemplate.
    */
   public void setRenderingEngineTemplateTitle(final String renderingEngineTemplateTitle)
   {
      this.renderingEngineTemplateTitle = renderingEngineTemplateTitle;
   }

   /**
    * @return the title for this renderingEngineTemplate.
    */
   public String getRenderingEngineTemplateTitle()
   {
      return (this.renderingEngineTemplateTitle == null && this.getRenderingEngineTemplateFileName() != null
              ? this.getRenderingEngineTemplateFileName().replaceAll("(.+)\\..*", "$1")
              : this.renderingEngineTemplateTitle);
   }

   /**
    * Sets the description for this renderingEngineTemplate.
    */
   public void setRenderingEngineTemplateDescription(final String renderingEngineTemplateDescription)
   {
      this.renderingEngineTemplateDescription = renderingEngineTemplateDescription;
   }

   /**
    * @return the description for this renderingEngineTemplate.
    */
   public String getRenderingEngineTemplateDescription()
   {
      return this.renderingEngineTemplateDescription;
   }

   public void setDefaultWorkflowName(final String[] defaultWorkflowName)
   {
      assert defaultWorkflowName.length == 1;
      this.defaultWorkflowName = (NO_DEFAULT_WORKFLOW_SELECTED.equals(defaultWorkflowName[0])
                                  ? null
                                  : defaultWorkflowName[0]);
   }

   public WorkflowDefinition getDefaultWorkflowDefinition()
   {
      return (this.defaultWorkflowName == null
              ? null
              : this.workflowService.getDefinitionByName("jbpm$" + this.defaultWorkflowName));
   }

   public String[] getDefaultWorkflowName()
   {
      return new String[] { 
         (this.defaultWorkflowName == null 
          ? NO_DEFAULT_WORKFLOW_SELECTED 
          : this.defaultWorkflowName)
      };
   }

   /**
    * @return List of UI items to represent the available Workflows for all websites
    */
   public List<UIListItem> getDefaultWorkflowChoices()
   {
      // TODO: add list of workflows from config
      // @see org.alfresco.web.wcm.FormDetailsDialog#getWorkflowList()
      final List<WorkflowDefinition> workflowDefs = this.workflowService.getDefinitions();
      final List<UIListItem> result = new ArrayList<UIListItem>(workflowDefs.size() + 1);

      UIListItem item = new UIListItem();
      item.setValue(NO_DEFAULT_WORKFLOW_SELECTED);
      item.setLabel("None");
      item.setImage(WebResources.IMAGE_WORKFLOW_32);
      result.add(item);

      for (WorkflowDefinition workflowDef : workflowDefs)
      {
         item = new UIListItem();
         item.setValue(workflowDef.getName());
         item.setLabel(workflowDef.getTitle());
         item.setDescription(workflowDef.getDescription());
         item.setImage(WebResources.IMAGE_WORKFLOW_32);
         result.add(item);
      }
      return result;
   }

   // ------------------------------------------------------------------------------
   // Service Injection
   
   /**
    * @param contentService The contentService to set.
    */
   public void setContentService(final ContentService contentService)
   {
      this.contentService = contentService;
   }

   /**
    * @param mimetypeService The mimetypeService to set.
    */
   public void setMimetypeService(final MimetypeService mimetypeService)
   {
      this.mimetypeService = mimetypeService;
   }

   /**
    * @param workflowService The workflowService to set.
    */
   public void setWorkflowService(final WorkflowService workflowService)
   {
      this.workflowService = workflowService;
   }
   
   // ------------------------------------------------------------------------------
   // Helper Methods
   
   /**
    * Clear the uploaded form, clearing the specific Upload component by Id
    */
   protected void clearUpload(final String id)
   {
      // remove the file upload bean from the session
      FacesContext ctx = FacesContext.getCurrentInstance();
      FileUploadBean fileBean =
         (FileUploadBean)ctx.getExternalContext().getSessionMap().get(FileUploadBean.getKey(id));
      if (fileBean != null)
      {
         fileBean.setFile(null);
         fileBean.setFileName(null);
      }
   }
}
