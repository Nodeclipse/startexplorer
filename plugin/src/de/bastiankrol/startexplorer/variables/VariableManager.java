package de.bastiankrol.startexplorer.variables;

import static de.bastiankrol.startexplorer.Activator.getLogFacility;
import static de.bastiankrol.startexplorer.util.Util.getName;
import static de.bastiankrol.startexplorer.util.Util.getPath;
import static de.bastiankrol.startexplorer.util.Util.separateNameAndExtension;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IDynamicVariable;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.IValueVariable;
import org.eclipse.core.variables.VariablesPlugin;

import de.bastiankrol.startexplorer.util.MessageDialogHelper;

/**
 * Provides access to the Eclipse variables API (org.eclipse.core.variables).
 * 
 * @author Bastian Krol
 */
public class VariableManager
{
  /**
   * prefix for variables
   */
  public static final String VAR_BEGIN = "${";

  /**
   * suffix for variables
   */
  public static final String VAR_END = "}";

  private static final String RESOURCE_PATH = "resource_path";
  private static final String RESOURCE_PARENT = "resource_parent";
  private static final String RESOURCE_NAME = "resource_name";
  private static final String RESOURCE_WIHTOUT_EXTENSION = "resource_name_without_extension";
  private static final String RESOURCE_EXTENSION = "resource_extension";

  /**
   * variable for resource path
   */
  public static final String RESOURCE_PATH_VAR = VAR_BEGIN + RESOURCE_PATH
      + VAR_END;

  /**
   * variable for resource parent
   */
  public static final String RESOURCE_PARENT_VAR = VAR_BEGIN + RESOURCE_PARENT
      + VAR_END;

  /**
   * variable for resource name
   */
  public static final String RESOURCE_NAME_VAR = VAR_BEGIN + RESOURCE_NAME
      + VAR_END;

  public static final String RESOURCE_NAME_WIHTOUT_EXTENSION_VAR = VAR_BEGIN
      + RESOURCE_WIHTOUT_EXTENSION + VAR_END;

  public static final String RESOURCE_EXTENSION_VAR = VAR_BEGIN
      + RESOURCE_EXTENSION + VAR_END;

  private IStringVariableManager variableManager;
  private MessageDialogHelper messageDialogHelper;

  public VariableManager()
  {
    this.variableManager = VariablesPlugin.getDefault()
        .getStringVariableManager();
    this.messageDialogHelper = new MessageDialogHelper();
  }

  VariableManager(IStringVariableManager eclipseVariableManager,
      MessageDialogHelper messageDialogHelper)
  {
    this.variableManager = eclipseVariableManager;
    this.messageDialogHelper = messageDialogHelper;
  }

  public Map<String, String> getNamesWithDescriptions()
  {
    Map<String, String> variableNamesWithDescription = new LinkedHashMap<String, String>();
    IValueVariable[] valueVariables = variableManager.getValueVariables();
    for (IValueVariable variable : valueVariables)
    {
      variableNamesWithDescription.put("${" + variable.getName() + "}",
          variable.getDescription());
    }
    IDynamicVariable[] dynamicVariables = variableManager.getDynamicVariables();
    for (IDynamicVariable variable : dynamicVariables)
    {
      variableNamesWithDescription.put("${" + variable.getName() + "}",
          variable.getDescription());
    }
    return variableNamesWithDescription;
  }

  /**
   * Replaces variables in the given command parts.
   * 
   * @param cmdArray the command parts.
   * @param file the file, can be null
   * @param wrapFileParts
   * @param escapeFileParts
   */
  public void replaceAllVariablesInCommand(String[] cmdArray, File file,
      boolean wrapFileParts, boolean escapeFileParts)
  {
    for (int i = 0; i < cmdArray.length; i++)
    {
      cmdArray[i] = replaceStartExplorerVariables(cmdArray[i], file,
          wrapFileParts, escapeFileParts);
      cmdArray[i] = replaceEclipseVariables(cmdArray[i]);
    }
  }

  private String replaceStartExplorerVariables(String command, File file,
      boolean wrapFileParts, boolean escapeFileParts)
  {
    // TODO Integrate "old" StartExplorer variables in standard Eclipse
    // variables mechanism, that is, provide them as an extension as dynamic
    // variables.
    String path = getPath(file, wrapFileParts, escapeFileParts);
    command = command.replace(RESOURCE_PATH_VAR, path);
    String name = getName(file, wrapFileParts, escapeFileParts);
    command = command.replace(RESOURCE_NAME_VAR, name);
    File parent = file.getParentFile();
    String parentPath;
    if (parent != null)
    {
      parentPath = getPath(parent, wrapFileParts, escapeFileParts);
      command = command.replace(RESOURCE_PARENT_VAR, parentPath);
    }
    else if (command.contains(RESOURCE_PARENT_VAR))
    {
      getLogFacility().logWarning(
          "The custom command contains the variable " + RESOURCE_PARENT_VAR
              + " but the file " + file.getAbsolutePath() + "has no parent.");
    }

    String[] nameWithoutExtensionAndExtension = separateNameAndExtension(file,
        wrapFileParts, escapeFileParts);
    command = command.replace(RESOURCE_NAME_WIHTOUT_EXTENSION_VAR,
        nameWithoutExtensionAndExtension[0]);
    command = command.replace(RESOURCE_EXTENSION_VAR,
        nameWithoutExtensionAndExtension[1]);
    return command;
  }

  private String replaceEclipseVariables(String command)
  {
    try
    {
      return this.variableManager.performStringSubstitution(command);
    }
    catch (CoreException e)
    {
      String message;
      if (e.getMessage() != null)
      {
        message = e.getMessage();
      }
      else
      {
        message = "A "
            + e.getClass().getSimpleName()
            + " occured while resolving the variables in the custom command <"
            + command
            + ">. No further information about the underlying problem is available.";
      }
      this.messageDialogHelper.displayErrorMessage(
          "Error resolving variables in custom command", message);
      throw new RuntimeException(e);
    }
  }
}
