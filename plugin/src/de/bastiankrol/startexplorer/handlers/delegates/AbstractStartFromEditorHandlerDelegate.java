package de.bastiankrol.startexplorer.handlers.delegates;

import static de.bastiankrol.startexplorer.Activator.getLogFacility;
import static de.bastiankrol.startexplorer.Activator.getPluginContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;

import de.bastiankrol.startexplorer.ResourceType;
import de.bastiankrol.startexplorer.crossplatform.Capabilities;
import de.bastiankrol.startexplorer.util.Validator;

/**
 * Examines the selected region in a text file, tries to interpret it as a
 * Windows path and
 * <ul>
 * <li>opens a Windows Explorer for the directory in the location described by
 * that path or</li>
 * <li>starts a system application for that file.</li>
 * </ul>
 * 
 * @author Bastian Krol
 */
public abstract class AbstractStartFromEditorHandlerDelegate extends
    AbstractHandlerDelegate
{

  public Object execute(ExecutionEvent event) throws ExecutionException
  {
    Object applicationContext = event.getApplicationContext();
    if (!(applicationContext instanceof IEvaluationContext))
    {
      getLogFacility().logWarning(
          "Current application context is not an IEvaluationContext.");
      return null;
    }
    IEvaluationContext appContext = (IEvaluationContext) applicationContext;
    ISelection selection = (ISelection) appContext
        .getVariable(ISources.ACTIVE_MENU_SELECTION_NAME);
    if (selection == null && !this.alwaysUseFileOpenedInEditor())
    {
      getLogFacility().logWarning(
          "Current selection is null, no action is taken.");
      return null;
    }
    if (selection.isEmpty() && !this.alwaysUseFileOpenedInEditor())
    {
      getLogFacility().logWarning(
          "Current selection is empty, no action is taken.");
      return null;
    }
    return this.executeForSelection(event, selection, appContext);
  }

  /**
   * Executes the commnd for a text selection.
   * 
   * @param event the Eclipse event
   * @param selection the selection object
   * @param appContext the evaluation context
   * @return null
   * @throws ExecutionException if things go wrong
   */
  public Object executeForSelection(ExecutionEvent event, ISelection selection,
      IEvaluationContext appContext) throws ExecutionException
  {
    String selectedText;
    selectedText = this.extractStringFromSelection(selection);
    if (selectedText == null && !this.alwaysUseFileOpenedInEditor())
    {
      getLogFacility().logWarning("Current selection's text is null.");
      return null;
    }
    else if (selectedText.equals("") || this.alwaysUseFileOpenedInEditor())
    {
      this.executeForFileOpenedInEditor(event, appContext);
      return null;
    }
    else if (this.shouldInterpretTextSelectionAsFileName())
    {
      this.executeForSelectedText(event, selectedText);
      return null;
    }
    else
    {
      try
      {
        this.doActionForSelectedText(selectedText);
      }
      catch (IOException e)
      {
        MessageDialog
            .openError(
                HandlerUtil.getActiveShellChecked(event),
                "IO Error",
                "An IO error occured while trying to create a temp file to pass the selected text to the application. Message: "
                    + e.getLocalizedMessage());
        return null;
      }
    }
    return null;
  }

  private void executeForFileOpenedInEditor(ExecutionEvent event,
      IEvaluationContext appContext) throws ExecutionException
  {
    Object editorInputObject = appContext.getParent().getVariable(
        "activeEditorInput");

    if (editorInputObject != null
        && IFileEditorInput.class
            .isAssignableFrom(editorInputObject.getClass()))
    {
      IFileEditorInput editorInput = (IFileEditorInput) editorInputObject;
      IResource fileInEditor = editorInput.getFile();
      File file = this.resourceToFile(fileInEditor, this.getResourceType(),
          event);
      AbstractStartFromResourceHandlerDelegate startFromResourceHandlerDelegate = this
          .getAppropriateStartFromResourceHandlerDelegate();
      if (startFromResourceHandlerDelegate != null)
      {
        startFromResourceHandlerDelegate.doActionForFileList(Collections
            .singletonList(file));
        return;
      }
      else
      {
        MessageDialog
            .openError(
                HandlerUtil.getActiveShellChecked(event),
                "Empty text selection",
                "The current selection is an empty text selection, and since this command is not enabled for resources it can not be invoked for the resource opened in the editor instead.");
        return;
      }
    }
    else
    {
      getPluginContext()
          .getLogFacility()
          .logWarning(
              "The current selection is an empty text selection, so the command was invoked for the resource opened in the editor. "
                  + "But the object fetched by event.getApplicationContext().getParent().getVariable(\"activeEditorInput\") is not of expected type IFileEditorInput: "
                  + editorInputObject);
    }
  }

  private void executeForSelectedText(ExecutionEvent event, String selectedText)
      throws ExecutionException
  {
    selectedText = selectedText.trim();
    Validator.MaybeFile maybeFile = this.getValidator().checkPath(selectedText,
        this.getResourceType());
    if (maybeFile.file != null)
    {
      this.doActionForFile(maybeFile.file);
      return;
    }
    // If selectedText is not a valid file, it might be a valid URL. Some
    // actions support URLs (on some platforms), so we might give it a try.
    else if (this
        .areUrlsSupported(this.getRuntimeExecCalls().getCapabilities()))
    {
      URL url = this.getValidator().checkUrl(selectedText, event);
      if (url != null)
      {
        this.doActionForUrl(url);
        return;
      }
    }

    // Unable to interprete selectedText as file or as URL, show message dialog
    // explaining failure.
    this.getValidator().showMessageFor(maybeFile.reason, selectedText, event);
  }

  /**
   * Determines whether the text selection is to be interpreted as a file name.
   * By default, this method returns <code>true</code>. Subclasses may override
   * this method, in this case they should also override
   * {@link #doActionForSelectedText(String)}.
   * 
   * @return <code>true</code>
   */
  protected boolean shouldInterpretTextSelectionAsFileName()
  {
    return true;
  }

  /**
   * Determines whether this handler should always act on the resource currently
   * opened in the editor instead of only doing so when the current text
   * selection is empty. By default, this method returns <code>false</code>.
   * Subclasses may override this method.
   * 
   * @return <code>false</code>
   */
  protected boolean alwaysUseFileOpenedInEditor()
  {
    return false;
  }

  /**
   * Executes the appropriate action for the given <code>selectedText</code>; by
   * default this method does nothing. This method needs to be overridden when
   * {@link #shouldInterpretTextSelectionAsFileName()} is overridden.
   * 
   * @param selectedText the String to do something with
   * @throws IOException
   */
  protected void doActionForSelectedText(String selectedText)
      throws IOException
  {
    // Empty default implementation
  }

  /**
   * Returns the resource type appropriate for this handler.
   * 
   * @return the resource type appropriate for this handler.
   */
  protected abstract ResourceType getResourceType();

  /**
   * Executes the appropriate action for the given <code>file</code>.
   * 
   * @param file the File object to do something with
   */
  protected abstract void doActionForFile(File file);

  /**
   * Executes the appropriate action for the given <code>url</code>, if that is
   * supported. Must not be called if {@code this.areUrlsSuppored()} returns
   * {@code false}. Subclasses that override this should also override
   * {@link #areUrlsSupported()}.
   * 
   * @param url the URL object to do something with
   */
  protected void doActionForUrl(URL url)
  {
    throw new UnsupportedOperationException(
        "This action does not support URLs.");
  }

  /**
   * Checks if this action can also be called with an URL on the current
   * platform. The default implementation is to always return {@¢ode
   * false}.
   * 
   * Subclasses that override this, also need to override
   * {@link #doActionForUrl(URL)}.
   * 
   * @param capabilities the platform's capabilities
   * 
   * @return {@code true} if this action might be able to handle URLs.
   */
  protected boolean areUrlsSupported(Capabilities capabilities)
  {
    // default: no URL support
    return false;
  }

  private String extractStringFromSelection(ISelection selection)
  {
    if (!(selection instanceof ITextSelection))
    {
      String message = "Current selection is not a text selection (ITextSelection), [selection.getClass(): "
          + selection.getClass()
          + ", selection.toString(): "
          + selection.toString() + "]";
      getLogFacility().logWarning(message);
      return null;
    }
    else
    {
      ITextSelection textSelection = (ITextSelection) selection;
      String pathString = textSelection.getText();
      return pathString;
    }
  }

  /**
   * Returns a handler delegate object that handles the same action as this
   * handler, but for a structured resource selection instead of a text
   * selection.
   * 
   * @return an AbstractStartFromResourceHandlerDelegate
   */
  abstract AbstractStartFromResourceHandlerDelegate getAppropriateStartFromResourceHandlerDelegate();
}
