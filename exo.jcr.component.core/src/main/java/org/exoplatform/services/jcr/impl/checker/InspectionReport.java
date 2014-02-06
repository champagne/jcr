/*
 * Copyright (C) 2011 eXo Platform SAS.
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
package org.exoplatform.services.jcr.impl.checker;

import org.exoplatform.commons.utils.SecurityHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.PrivilegedExceptionAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Text-based inspection log implementation.
 * 
 * @author <a href="mailto:skarpenko@exoplatform.com">Sergiy Karpenko</a>
 * @version $Id: InspectionReport.java 34360 6.10.2011 skarpenko $
 */
public class InspectionReport
{
   private static final String COMMENT = "//";

   private static final String DELIMITER = "\n";

   private static final String WHITE_SPACE = " ";

   private Writer writer;

   private boolean reportHasInconsistency;

   private String reportPath;

   private boolean isMulti;

   private ThreadLocal<List<String>> comments=new ThreadLocal<List<String>>();

   private ThreadLocal<List<String>> logBrokenObjects=new ThreadLocal<List<String>>();

   private ThreadLocal<Map<String,Throwable>> logExceptions=new ThreadLocal<Map<String,Throwable>>();

   /**
    * InspectionReport constructor.
    */
   public InspectionReport(String forRepository) throws IOException
   {
      final File reportFile =
         new File("report-" + forRepository + "-" + new SimpleDateFormat("dd-MMM-yy-HH-mm").format(new Date()) + ".txt");

      SecurityHelper.doPrivilegedIOExceptionAction(new PrivilegedExceptionAction<Void>()
      {
         public Void run() throws IOException
         {
            reportPath = reportFile.getAbsolutePath();
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reportFile)));

            return null;
         }
      });

   }

   public void init()
   {
      comments.set(new ArrayList<String>());
      logBrokenObjects.set(new ArrayList<String>());
      logExceptions.set(new HashMap<String, Throwable>());
   }

   public synchronized void flush() throws IOException
   {
      for (String message : comments.get())
      {
         writeLine(message);
      }
      for (String brokenObject : logBrokenObjects.get())
      {
         writeLine(brokenObject);
         writer.write(DELIMITER);
      }
      for (String key : logExceptions.get().keySet())
      {
         writeLine(key);
         writeStackTrace(logExceptions.get().get(key));
      }
      logExceptions.get().clear();
      comments.get().clear();
      logBrokenObjects.get().clear();
      writer.flush();
   }

   /**
    * Indicates if report has inconsistency info or not.
    */
   public boolean hasInconsistency()
   {
      return reportHasInconsistency;
   }

   /**
    * Adds comment to log.
    */
   public void logComment(String message) throws IOException
   {
      if (isMulti)
      {
         comments.get().add(message);
      }
      else
      {
         writeLine(message);
         writer.flush();
      }
   }

   /**
    * Adds description to log.
    */
   public void logDescription(String description) throws IOException
   {
      if (isMulti)
      {
         comments.get().add(description);
      }
      else
      {
         writeLine(description);
         writer.flush();
      }
   }

   /**
    * Adds detailed event to log.
    */
   public void logBrokenObjectAndSetInconsistency(String brokenObject) throws IOException
   {
      setInconsistency();

      if (isMulti)
      {
         logBrokenObjects.get().add(brokenObject);
      }
      else
      {
         writer.write(brokenObject);
         writer.write(DELIMITER);
         writer.flush();

      }
   }

   /**
    * Adds exception with full stack trace.
    */
   public void logExceptionAndSetInconsistency(String message, Throwable e) throws IOException
   {
      setInconsistency();
      if (isMulti)
      {
         logExceptions.get().put(message, e);
      }
      else
      {
         writeLine(message);
         writeStackTrace(e);
         writer.flush();

      }
   }

   /**
    * Closes report and frees all allocated resources. 
    */
   public void close() throws IOException
   {
      writer.close();
   }

   /**
    * Returns the absolute path to report file. 
    */
   public String getReportPath()
   {
      return reportPath;
   }

   private void setInconsistency()
   {
      reportHasInconsistency = true;
   }

   private void writeLine(String message) throws IOException
   {
      writer.write(COMMENT);
      writer.write(message);
      writer.write(DELIMITER);
      writer.flush();
   }

   private void writeStackTrace(Throwable e) throws IOException
   {
      writeLine(e.getMessage());
      writeLine(e.toString());
      StackTraceElement[] trace = e.getStackTrace();
      for (int i = 0; i < trace.length; i++)
      {
         writeLine("\tat " + trace[i]);
      }

      Throwable ourCause = e.getCause();
      if (ourCause != null)
      {
         writeLine("Cause:");
         writeStackTrace(ourCause);
      }
   }

   /**
    * Enable MultiThreading mode.
    */
   public void enableMultiThreadingMode()
   {
      this.isMulti = true;
   }

   /**
    * Disable MultiThreading mode.
    */
   public void disableMultiThreadingMode()
   {
      this.isMulti = false;
   }
}
