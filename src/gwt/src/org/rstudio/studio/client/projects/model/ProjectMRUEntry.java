/*
 * ProjectMRUEntry.java
 *
 * Copyright (C) 2023 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */package org.rstudio.studio.client.projects.model;

import org.rstudio.core.client.StringUtil;

public class ProjectMRUEntry {

   public ProjectMRUEntry(String projectFilePath, String projectName)
   {
      projectFilePath_ = StringUtil.ensureNotNull(projectFilePath);
      projectName_ = StringUtil.ensureNotNull(projectName).trim();
   }

   public ProjectMRUEntry(String mruEntry)
   {
      if (StringUtil.isNullOrEmpty(mruEntry))
      {
         projectFilePath_ = "";
         projectName_ = "";
         return;
      }

      int tabPos = mruEntry.indexOf("\t");
      if (tabPos == -1)
      {
         projectFilePath_ = mruEntry;
         projectName_ = "";
      } else {
         projectFilePath_ = mruEntry.substring(0, tabPos);
         projectName_ = mruEntry.substring(tabPos + 1).trim();
      }
   }

   public String getProjectFilePath()
   {
      return projectFilePath_;
   }

   public String getProjectName()
   {
      return projectName_;
   }

   /**
   * @return the value to store in the project MRU; consists of the project file path followed
   * by a tab and the project name (if any); returns empty string if no project file path
   */
   public String getMRUValue()
   {
      if (projectFilePath_ == "")
         return "";
      else
         return projectFilePath_ +
               (StringUtil.isNullOrEmpty(projectName_) ? "" : "\t" + projectName_);
   }

   private final String projectFilePath_;
   private final String projectName_;
}
