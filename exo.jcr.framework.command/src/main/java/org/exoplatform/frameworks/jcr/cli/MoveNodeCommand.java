/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.frameworks.jcr.cli;

import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Workspace;

/**
 * Created by The eXo Platform SAS
 * 
 * @author Vitaliy Obmanjuk
 * @version $Id: $
 */

public class MoveNodeCommand extends AbstractCliCommand
{

   public boolean perform(CliAppContext ctx)
   {
      String output = "";
      try
      {
         String srcAbsPath = ctx.getParameter(0);
         String destAbsPath = ctx.getParameter(1);
         Workspace workspace = ctx.getSession().getWorkspace();
         workspace.move(srcAbsPath, destAbsPath);
         output = "Node [" + srcAbsPath + "] has been moved to [" + destAbsPath + "] successfully \n";
      }
      catch (ParameterNotFoundException e)
      {
         output = "Can't execute command - " + e.getMessage() + "\n";
      }
      catch (LoginException e)
      {
         output = "Can't execute command - " + e.getMessage() + "\n";
      }
      catch (NoSuchWorkspaceException e)
      {
         output = "Can't execute command - " + e.getMessage() + "\n";
      }
      catch (RepositoryException e)
      {
         output = "Can't execute command - " + e.getMessage() + "\n";
      }
      ctx.setOutput(output);
      return false;
   }
}
