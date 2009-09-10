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
package org.exoplatform.services.jcr;

import junit.framework.TestCase;

import org.exoplatform.container.StandaloneContainer;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.CredentialsImpl;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.core.WorkspaceContainerFacade;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ReaderSpoolFileHolder;
import org.exoplatform.services.jcr.impl.util.io.FileCleaner;
import org.exoplatform.services.jcr.impl.util.io.WorkspaceFileCleanerHolder;
import org.exoplatform.services.jcr.storage.WorkspaceDataContainer;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Random;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.Workspace;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:geaz@users.sourceforge.net">Gennady Azarenkov </a>
 * @version $Id: BaseStandaloneTest.java 11908 2008-03-13 16:00:12Z ksm $
 */
public abstract class BaseStandaloneTest extends TestCase
{

   protected static Log log = ExoLogger.getLogger("jcr.JCRTest");

   protected static String TEMP_PATH = "./temp/fsroot";

   protected static String WORKSPACE = "ws";

   protected SessionImpl session;

   protected RepositoryImpl repository;

   protected CredentialsImpl credentials;

   protected Workspace workspace;

   protected RepositoryService repositoryService;

   protected Node root;

   protected ValueFactory valueFactory;

   protected StandaloneContainer container;

   public int maxBufferSize = 200 * 1024;

   public FileCleaner fileCleaner;

   public ReaderSpoolFileHolder holder;

   protected class CompareStreamException extends Exception
   {

      CompareStreamException(String message)
      {
         super(message);
      }

      CompareStreamException(String message, Throwable e)
      {
         super(message, e);
      }
   }

   public void setUp() throws Exception
   {

      String containerConf =
         BaseStandaloneTest.class.getResource(System.getProperty("jcr.test.configuration.file")).toString();
      String loginConf = BaseStandaloneTest.class.getResource("/login.conf").toString();

      StandaloneContainer.addConfigurationURL(containerConf);
      // .addConfigurationPath("src/test/java/conf/standalone/test-configuration.xml");
      // .addConfigurationPath("src/test/java/conf/standalone/test-configuration-sjdbc.xml");
      // .addConfigurationPath("src/test/java/conf/standalone/test-configuration-sjdbc.pgsql.xml");
      // .addConfigurationPath("src/test/java/conf/standalone/test-configuration-sjdbc.ora.xml");
      // .addConfigurationPath("src/test/java/conf/standalone/test-configuration-mjdbc.mysql.xml");

      container = StandaloneContainer.getInstance();

      if (System.getProperty("java.security.auth.login.config") == null)
         System.setProperty("java.security.auth.login.config", loginConf);

      credentials = new CredentialsImpl("admin", "admin".toCharArray());

      repositoryService = (RepositoryService)container.getComponentInstanceOfType(RepositoryService.class);
      repository = (RepositoryImpl)repositoryService.getDefaultRepository();

      session = (SessionImpl)repository.login(credentials, "ws");
      workspace = session.getWorkspace();
      root = session.getRootNode();
      valueFactory = session.getValueFactory();

      initRepository();
      ManageableRepository repository = repositoryService.getDefaultRepository();
      WorkspaceContainerFacade wsc = repository.getWorkspaceContainer("ws");

      WorkspaceEntry wconf = (WorkspaceEntry)wsc.getComponent(WorkspaceEntry.class);

      maxBufferSize =
         wconf.getContainer().getParameterInteger(WorkspaceDataContainer.MAXBUFFERSIZE_PROP,
            WorkspaceDataContainer.DEF_MAXBUFFERSIZE);

      WorkspaceFileCleanerHolder wfcleaner =
         (WorkspaceFileCleanerHolder)wsc.getComponent(WorkspaceFileCleanerHolder.class);
      fileCleaner = wfcleaner.getFileCleaner();
      holder = new ReaderSpoolFileHolder();

   }

   protected void tearDown() throws Exception
   {

      if (session != null)
      {
         try
         {
            session.refresh(false);
            Node rootNode = session.getRootNode();
            if (rootNode.hasNodes())
            {
               // clean test root
               for (NodeIterator children = rootNode.getNodes(); children.hasNext();)
               {
                  Node node = children.nextNode();
                  if (!node.getPath().startsWith("/jcr:system"))
                  {
                     // log.info("DELETing ------------- "+node.getPath());
                     node.remove();
                  }
               }
               session.save();
            }
         }
         catch (Exception e)
         {
            log.error("tearDown() ERROR " + getClass().getName() + "." + getName() + " " + e, e);
         }
         finally
         {
            session.logout();
         }
      }
      super.tearDown();

      // log.info("tearDown() END " + getClass().getName() + "." + getName());
   }

   protected abstract String getRepositoryName();

   public void initRepository() throws RepositoryException
   {
   }

   // ====== utils =======

   protected void checkItemsExisted(String[] exists, String[] notExists) throws RepositoryException
   {
      String path = null;
      if (exists != null)
      {
         try
         {
            for (String nodePath : exists)
            {
               path = nodePath;
               session.getItem(path);
            }
         }
         catch (PathNotFoundException e)
         {
            fail("Item must exists " + path + ". " + e.getMessage());
         }
      }
      if (notExists != null)
      {
         try
         {
            for (String nodePath : notExists)
            {
               session.getItem(nodePath);
               fail("Item must not exists " + nodePath);
            }
         }
         catch (PathNotFoundException e)
         {
            // ok
         }
      }
   }

   protected void checkNodesExistedByUUID(String[] exists, String[] notExists) throws RepositoryException
   {
      String uuid = null;
      if (exists != null)
      {
         try
         {
            for (String nodePath : exists)
            {
               uuid = nodePath;
               session.getNodeByUUID(uuid);
            }
         }
         catch (PathNotFoundException e)
         {
            fail("Node must exists, UUID " + uuid + ". " + e.getMessage());
         }
      }
      if (notExists != null)
      {
         try
         {
            for (String nodeUUID : notExists)
            {
               session.getNodeByUUID(nodeUUID);
               fail("Node must not exists, UUID " + nodeUUID);
            }
         }
         catch (PathNotFoundException e)
         {
            // ok
         }
      }
   }

   protected void compareStream(InputStream etalon, InputStream data) throws IOException
   {
      try
      {
         compareStream(etalon, data, 0, 0, -1);
      }
      catch (CompareStreamException e)
      {
         fail(e.getMessage());
      }
   }

   /**
    * Compare etalon stream with data stream begining from the offset in etalon
    * and position in data. Length bytes will be readed and compared. if length
    * is lower 0 then compare streams till one of them will be read.
    * 
    * @param etalon
    * @param data
    * @param etalonPos
    * @param length
    * @param dataPos
    * @throws IOException
    */
   protected void compareStream(InputStream etalon, InputStream data, long etalonPos, long dataPos, long length)
      throws IOException, CompareStreamException
   {

      int dindex = 0;

      skipStream(etalon, etalonPos);
      skipStream(data, dataPos);

      byte[] ebuff = new byte[1024];
      int eread = 0;

      while ((eread = etalon.read(ebuff)) > 0)
      {

         byte[] dbuff = new byte[eread];
         int erindex = 0;
         while (erindex < eread)
         {
            int dread = -1;
            try
            {
               dread = data.read(dbuff);
            }
            catch (IOException e)
            {
               throw new CompareStreamException("Streams is not equals by length or data stream is unreadable. Cause: "
                  + e.getMessage());
            }

            if (dread == -1)
               throw new CompareStreamException(
                  "Streams is not equals by length. Data end-of-stream reached at position " + dindex);

            for (int i = 0; i < dread; i++)
            {
               byte eb = ebuff[i];
               byte db = dbuff[i];
               if (eb != db)
                  throw new CompareStreamException("Streams is not equals. Wrong byte stored at position " + dindex
                     + " of data stream. Expected 0x" + Integer.toHexString(eb) + " '" + new String(new byte[]{eb})
                     + "' but found 0x" + Integer.toHexString(db) + " '" + new String(new byte[]{db}) + "'");

               erindex++;
               dindex++;
               if (length > 0 && dindex >= length)
                  return; // tested length reached
            }

            if (dread < eread)
               dbuff = new byte[eread - dread];
         }
      }

      if (data.available() > 0)
         throw new CompareStreamException("Streams is not equals by length. Data stream contains more data. Were read "
            + dindex);
   }

   protected void skipStream(InputStream stream, long pos) throws IOException
   {
      long curPos = pos;
      long sk = 0;
      while ((sk = stream.skip(curPos)) > 0)
      {
         curPos -= sk;
      };
      if (sk < 0)
         fail("Can not read the stream (skip bytes)");
      if (curPos != 0)
         fail("Can not skip bytes from the stream (" + pos + " bytes)");
   }

   protected File createBLOBTempFile(int sizeInKb) throws IOException
   {
      return createBLOBTempFile("exo_jcr_test_temp_file_", sizeInKb);
   }

   protected File createBLOBTempFile(String prefix, int sizeInKb) throws IOException
   {
      // create test file
      byte[] data = new byte[1024]; // 1Kb

      File testFile = File.createTempFile(prefix, ".tmp");
      FileOutputStream tempOut = new FileOutputStream(testFile);
      Random random = new Random();

      for (int i = 0; i < sizeInKb; i++)
      {
         random.nextBytes(data);
         tempOut.write(data);
      }
      tempOut.close();
      testFile.deleteOnExit(); // delete on test exit
      log.info("Temp file created: " + testFile.getAbsolutePath() + " size: " + testFile.length());
      return testFile;
   }

   protected void checkMixins(String[] mixins, NodeImpl node)
   {
      try
      {
         String[] nodeMixins = node.getMixinTypeNames();
         assertEquals("Mixins count is different", mixins.length, nodeMixins.length);

         compareMixins(mixins, nodeMixins);
      }
      catch (RepositoryException e)
      {
         fail("Mixins isn't accessible on the node " + node.getPath());
      }
   }

   protected void compareMixins(String[] mixins, String[] nodeMixins)
   {
      nextMixin : for (String mixin : mixins)
      {
         for (String nodeMixin : nodeMixins)
         {
            if (mixin.equals(nodeMixin))
               continue nextMixin;
         }

         fail("Mixin '" + mixin + "' isn't accessible");
      }
   }

   protected String memoryInfo()
   {
      String info = "";
      info =
         "free: " + mb(Runtime.getRuntime().freeMemory()) + "M of " + mb(Runtime.getRuntime().totalMemory())
            + "M (max: " + mb(Runtime.getRuntime().maxMemory()) + "M)";
      return info;
   }

   // bytes to Mbytes
   protected String mb(long mem)
   {
      return String.valueOf(Math.round(mem * 100d / (1024d * 1024d)) / 100d);
   }

   protected String execTime(long from)
   {
      return Math.round(((System.currentTimeMillis() - from) * 100.00d / 60000.00d)) / 100.00d + "min";
   }

   /**
    * Force a garbage collector.
    */
   protected static void forceGC()
   {
      WeakReference<Object> dumbReference = new WeakReference<Object>(new Object());
      // A loop that will wait GC, using the minimal time as possible
      while (dumbReference.get() != null)
      {
         System.gc();
         try
         {
            Thread.sleep(500);
         }
         catch (InterruptedException e)
         {
         }
      }
   }
}
