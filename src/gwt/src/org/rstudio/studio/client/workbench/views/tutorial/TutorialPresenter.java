/*
 * TutorialPresenter.java
 *
 * Copyright (C) 2009-20 by RStudio, Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.tutorial;

import org.rstudio.core.client.Debug;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.container.SafeMap;
import org.rstudio.core.client.js.JsObject;
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.application.events.InterruptStatusEvent;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.server.VoidServerRequestCallback;
import org.rstudio.studio.client.shiny.ShinyDisconnectNotifier;
import org.rstudio.studio.client.shiny.ShinyDisconnectNotifier.ShinyDisconnectSource;
import org.rstudio.studio.client.shiny.events.ShinyApplicationStatusEvent;
import org.rstudio.studio.client.shiny.model.ShinyApplicationParams;
import org.rstudio.studio.client.workbench.WorkbenchView;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.BasePresenter;
import org.rstudio.studio.client.workbench.views.tutorial.events.TutorialCommandEvent;
import org.rstudio.studio.client.workbench.views.tutorial.events.TutorialNavigateEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;

public class TutorialPresenter
      extends
         BasePresenter
      implements
         TutorialCommandEvent.Handler,
         TutorialNavigateEvent.Handler,
         ShinyApplicationStatusEvent.Handler,
         ShinyDisconnectSource,
         InterruptStatusEvent.Handler,
         LoadHandler
{
   public interface Binder extends CommandBinder<Commands, TutorialPresenter> {}
   
   public interface Display extends WorkbenchView
   {
      void back();
      void forward();
      void clear();
      void popout();
      void refresh();
      void home();
      
      String getUrl();
      String getName();
      
      void launchTutorial(Tutorial tutorial);
      
      void onTutorialStarted(Tutorial tutorial);
      void openTutorial(String url);
      
      HandlerRegistration addLoadHandler(LoadHandler handler);
      HandlerRegistration addTutorialNavigateHandler(TutorialNavigateEvent.Handler handler);
   }
   
   public static class Tutorial
   {
      public Tutorial(String tutorialName, String packageName)
      {
         tutorialName_ = tutorialName;
         packageName_ = packageName;
      }
      
      public String getTutorialName()
      {
         return tutorialName_;
      }
      
      public String getPackageName()
      {
         return packageName_;
      }
      
      public JsObject toJsObject()
      {
         JsObject object = JsObject.createJsObject();
         object.setString("name", tutorialName_);
         object.setString("package", packageName_);
         return object;
      }
      
      public static Tutorial fromJsObject(JsObject object)
      {
         return new Tutorial(
               object.getString("name"),
               object.getString("package"));
      }
      
      private final String tutorialName_;
      private final String packageName_;
   }
   
   @Inject
   protected TutorialPresenter(Display display,
                               EventBus events,
                               Commands commands,
                               Binder binder,
                               TutorialServerOperations server)
   {
      super(display);
      
      binder.bind(commands, this);
      
      display_ = display;
      events_ = events;
      commands_ = commands;
      server_ = server;
      
      paramsMap_ = new SafeMap<>();
      disconnectNotifier_ = new ShinyDisconnectNotifier(this);
      
      events_.addHandler(TutorialCommandEvent.TYPE, this);
      events_.addHandler(ShinyApplicationStatusEvent.TYPE, this);
      events_.addHandler(InterruptStatusEvent.TYPE, this);
      
      display_.addTutorialNavigateHandler(this);
      display_.addLoadHandler(this);
   }
   
   @Override
   public void onTutorialNavigate(TutorialNavigateEvent event)
   {
      manageCommands();
   }
   
   @Override
   public void onLoad(LoadEvent event)
   {
      manageCommands();
   }
   
   @Override
   public void onTutorialCommand(TutorialCommandEvent event)
   {
      String type = event.getType();
      
      // Navigate to the URL associated with an already-running tutorial.
      if (StringUtil.equals(type, TutorialCommandEvent.TYPE_NAVIGATE))
      {
         JsObject data = event.getData().cast();
         String url = data.getString("url");
         display_.openTutorial(url);
      }
      
      // Tutorial indexing completed; if we're viewing the list of available
      // tutorials, we should refresh to get an updated view.
      else if (StringUtil.equals(type, TutorialCommandEvent.TYPE_INDEXING_COMPLETED))
      {
         if (StringUtil.equals(display_.getUrl(), TutorialPresenter.URLS_HOME))
         {
            display_.refresh();
         }
      }
      
      // Invoked on launch; the project specifies a default tutorial and we should open
      // this tutorial on load.
      else if (StringUtil.equals(type, TutorialCommandEvent.TYPE_LAUNCH_DEFAULT_TUTORIAL))
      {
         final Tutorial tutorial = Tutorial.fromJsObject(event.getData());
         display_.launchTutorial(tutorial);
      }
      else
      {
         assert false : "Unknown tutorial command event '" + type + "'";
      }
   }
   
   @Override
   public void onShinyApplicationStatus(ShinyApplicationStatusEvent event)
   {
      // discard non-tutorial events
      String type = event.getParams().getViewerType();
      if (!type.startsWith(VIEWER_TYPE_TUTORIAL))
         return;
      
      String state = event.getParams().getState();
      if (StringUtil.equals(state, ShinyApplicationParams.STATE_STARTED))
      {
         display_.bringToFront();
         if (Desktop.hasDesktopFrame())
         {
            String url = event.getParams().getUrl();
            Desktop.getFrame().setTutorialUrl(url);
         }
         
         ShinyApplicationParams params = event.getParams();
         String tutorialName = params.getMeta().getString("name");
         String packageName  = params.getMeta().getString("package");
         String url = params.getUrl();
         
         paramsMap_.put(url, params);
         
         server_.tutorialStarted(
               tutorialName,
               packageName,
               url,
               new VoidServerRequestCallback());
         
         display_.openTutorial(url);
      }
      else if (StringUtil.equals(state, ShinyApplicationParams.STATE_STOPPING))
      {
         Debug.logToRConsole("Tutorial: stopping");
      }
      else if (StringUtil.equals(state, ShinyApplicationParams.STATE_STOPPED))
      {
         Debug.logToRConsole("Tutorial: stopped");
      }
   }
   
   @Override
   public void onInterruptStatus(InterruptStatusEvent event)
   {
      // TODO Auto-generated method stub
   }
   
   private void onTutorialStopped()
   {
      display_.home();
   }
   
   @Handler
   void onTutorialStop()
   {
      String url = display_.getUrl();
      ShinyApplicationParams params = paramsMap_.get(url);
      assert params != null :
         "no known tutorial associated with URL '" + url + "'";
            
      server_.tutorialStop(
            params.getMeta().getString("name"),
            params.getMeta().getString("package"),
            new ServerRequestCallback<Void>()
            {
               @Override
               public void onResponseReceived(Void response)
               {
                  onTutorialStopped();
               }

               @Override
               public void onError(ServerError error)
               {
                  Debug.logError(error);
               }
            });
   }
   
   @Handler
   void onTutorialBack()
   {
      display_.back();
   }
   
   @Handler
   void onTutorialForward()
   {
      display_.forward();
   }
   
   @Handler
   void onTutorialPopout()
   {
      display_.popout();
   }
   
   @Handler
   void onTutorialRefresh()
   {
      display_.refresh();
   }
   
   @Handler
   void onTutorialHome()
   {
      display_.home();
   }
   
   @Override
   public String getShinyUrl()
   {
      return display_.getUrl();
   }
   
   @Override
   public String getWindowName()
   {
      return display_.getName();
   }

   @Override
   public void onShinyDisconnect()
   {
      commands_.tutorialStop().setEnabled(false);
   }
   
   private void manageCommands()
   {
      boolean isShiny = TutorialUtil.isShinyUrl(display_.getUrl());
      commands_.tutorialRefresh().setEnabled(isShiny);
      commands_.tutorialStop().setEnabled(isShiny);
      commands_.tutorialStop().setVisible(isShiny);
      commands_.tutorialBack().setEnabled(isShiny);
      commands_.tutorialPopout().setEnabled(isShiny);
   }
   
   
   
   private final Display display_;
   private final EventBus events_;
   private final Commands commands_;
   private final TutorialServerOperations server_;
   private final ShinyDisconnectNotifier disconnectNotifier_;
   
   private final SafeMap<String, ShinyApplicationParams> paramsMap_;
   
   public static final String VIEWER_TYPE_TUTORIAL = "tutorial";
   
   public static final String URLS_HOME = GWT.getHostPageBaseURL() + "tutorial/home";
   
}
