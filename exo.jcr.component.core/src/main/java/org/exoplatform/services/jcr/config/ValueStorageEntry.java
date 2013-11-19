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
package org.exoplatform.services.jcr.config;

import java.util.List;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:gennady.azarenkov@exoplatform.com">Gennady Azarenkov</a>
 * @version $Id: ValueStorageEntry.java 11907 2008-03-13 15:36:21Z ksm $
 */

public class ValueStorageEntry extends ExtendedMappedParametrizedObjectEntry
{
   public static final String VALUE_STORAGE = "value-storage";
   
   private String id;

   private List<ValueStorageFilterEntry> filters;

   public ValueStorageEntry()
   {
      super(VALUE_STORAGE);
   }

   public ValueStorageEntry(String type, List<SimpleParameterEntry> params)
   {
      super(type, params, VALUE_STORAGE);
   }

   public List<ValueStorageFilterEntry> getFilters()
   {
      return filters;
   }

   public void setFilters(List<ValueStorageFilterEntry> filters)
   {
      this.filters = filters;
   }

   public String getId()
   {
      return id;
   }

   public void setId(String id)
   {
      this.id = id;
   }
}
