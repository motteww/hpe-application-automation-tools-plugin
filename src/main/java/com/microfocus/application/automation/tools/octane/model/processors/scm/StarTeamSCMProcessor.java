/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.model.processors.scm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.scm.SCMChange;
import com.hp.octane.integrations.dto.scm.SCMCommit;
import com.hp.octane.integrations.dto.scm.SCMData;
import com.hp.octane.integrations.dto.scm.SCMRepository;
import com.hp.octane.integrations.dto.scm.SCMType;
import com.microfocus.application.automation.tools.octane.configuration.SDKBasedLoggerProvider;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.SCM;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.Mailer;

class StarTeamSCMProcessor implements SCMProcessor {
  private static final Logger logger = SDKBasedLoggerProvider.getLogger(StarTeamSCMProcessor.class);
  private static final DTOFactory dtoFactory = DTOFactory.getInstance();

  @Override
  public SCMData getSCMData(AbstractBuild build, SCM scm) {
    List<ChangeLogSet<? extends ChangeLogSet.Entry>> changes = new ArrayList<>();
    changes.add(build.getChangeSet());
    return extractSCMData(build, scm, changes);
  }

  @Override
  public SCMData getSCMData(WorkflowRun run, SCM scm) {
    return extractSCMData(run, scm, run.getChangeSets());
  }

  @Override
  public CommonOriginRevision getCommonOriginRevision(final Run run) {
    return null;
  }

  private SCMData extractSCMData(Run run, SCM scm, List<ChangeLogSet<? extends ChangeLogSet.Entry>> changes) {
    if (!(scm.getClass().getName().equals("hudson.plugins.starteam.StarTeamSCM"))) {    
      throw new IllegalArgumentException("SCM type of StarTeamSCM was expected here, found '" + scm.getClass().getName() + "'");
    }

    SCMRepository repository = getRepository(scm);
    List<SCMCommit> commits = extractCommits(changes);

    return dtoFactory.newDTO(SCMData.class)
        .setRepository(repository)
        .setCommits(commits);
  }

  private SCMRepository getRepository(SCM starTeamSCM) {
    SCMRepository result = null;
    if (starTeamSCM != null) {
      try {
        String url = getSCMRepositoryURL(starTeamSCM);
        String branch = getSCMRepositoryBranch(starTeamSCM);
        result = dtoFactory.newDTO(SCMRepository.class).setType(SCMType.STARTEAM).setUrl(url).setBranch(branch);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
        logger.warn(e.getClass().getSimpleName() + " unable to extract data from starTeamSCM, no SCM repository info will be available.", e);
      }
    } else {
      logger.warn("starTeamSCM is null, no SCM repository info will be available.");
    }
    return result;
  }
  
  private List<SCMCommit> extractCommits(List<ChangeLogSet<? extends ChangeLogSet.Entry>> changes) {
    LinkedList<SCMCommit> tmpCommits = new LinkedList<>();
  
    for (ChangeLogSet<? extends ChangeLogSet.Entry> set : changes) {
      for (ChangeLogSet.Entry change : set) {
        if (change.getClass().getName().equals("hudson.plugins.starteam.changelog.StarTeamChangeLogEntry")) {
          List<SCMChange> tmpChanges = new ArrayList<>();
          User user = change.getAuthor();
          String userEmail = null;
  
          SCMChange tmpChange;
          try {
            tmpChange = dtoFactory.newDTO(SCMChange.class)
                .setType(getCommitType(change))
                .setFile(getFileName(change));
            tmpChanges.add(tmpChange);
  
            for (UserProperty property : user.getAllProperties()) {
              if (property instanceof Mailer.UserProperty) {
                userEmail = ((Mailer.UserProperty) property).getAddress();
              }
            }
  
            SCMCommit tmpCommit = dtoFactory.newDTO(SCMCommit.class)
                .setTime(getDate(change).getTime())
                .setUserEmail(userEmail)
                .setUser(change.getAuthor().getId())
                .setRevId(getRevision(change))
                .setComment(getComment(change))
                .setChanges(tmpChanges);
  
            tmpCommits.add(tmpCommit);
          } catch (Exception e) {
            logger.warn("failed to obtain commit information", e);
          }
        }
      }
    }
    return tmpCommits;
  }

  private Object callMethodViaReflection(Object object, String methodName) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Class<?> cls = object.getClass();
    Method methodTocall = cls.getDeclaredMethod(methodName); 
    Object returnValue = methodTocall.invoke(object); 
    return returnValue;
  }

  private String getSCMRepositoryBranch(SCM starTeamSCM) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
    String branch;
    if (usingStarTeamURL(starTeamSCM))
      branch = "";
    else {
      branch = getViewName(starTeamSCM);
    }
    return branch;
  }

  private String getViewName(SCM starTeamSCM) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(starTeamSCM, "getViewName");
    return (String) returnValue;
  }

  private String getSCMRepositoryURL(SCM starTeamSCM) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    String url;
    if (usingStarTeamURL(starTeamSCM)) {
      Object returnValue = callMethodViaReflection(starTeamSCM, "getStFolderUrl"); 
      url = (String) returnValue;
    }
    else {
      String hostname = getHostName(starTeamSCM);
      String port = getPort(starTeamSCM);
      String projectName = getProjectNamer(starTeamSCM);
      url = hostname + ":" + port + "/" + projectName;
    }
    return url;
  }

  private String getProjectNamer(SCM starTeamSCM) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(starTeamSCM, ("getProjectName"));
    return (String) returnValue;
  }

  private String getPort(SCM starTeamSCM) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(starTeamSCM, ("getPort"));
    return (String) returnValue;
  }

  private String getHostName(SCM starTeamSCM) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(starTeamSCM, ("getHostName"));
    return (String) returnValue;
  }

  private boolean usingStarTeamURL(SCM starTeamSCM) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(starTeamSCM, ("getStFolderUrl"));
    String getStFolderUrl = (String) returnValue;
    return getStFolderUrl != null && !getStFolderUrl.isEmpty();
  }

  private String getFileName(Entry change) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
    Object returnValue = callMethodViaReflection(change, ("getFileName"));
    return (String) returnValue;
  }

  private Date getDate(Entry change) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
    Object returnValue = callMethodViaReflection(change, ("getDate"));
    return (Date) returnValue;
  }

  private String getRevision(ChangeLogSet.Entry commit) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException, SecurityException {
    Integer viewMemberID = getViewMemberID(commit);
    Integer revisionNumber = getRevisionNumber(commit);
    String changeType = getChangeType(commit);
    String revID = "VMID: " + viewMemberID + " " + "Rev: " + revisionNumber;

    if (changeType.equals("DELETE")) {
      revID = revID + " (deleted)";
    }
    return revID;
  }

  private String getChangeType(ChangeLogSet.Entry commit) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException, SecurityException {
    Object changeType = callMethodViaReflection(commit, ("getChangeType"));
    return changeType.toString();
  }

  private Integer getRevisionNumber(ChangeLogSet.Entry commit) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(commit, ("getRevisionNumber"));
    return (Integer) returnValue;
  }

  private Integer getViewMemberID(ChangeLogSet.Entry commit) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Object returnValue = callMethodViaReflection(commit, ("getViewMemberId"));
    return (Integer) returnValue;
  }

  private String getComment(ChangeLogSet.Entry commit) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException, SecurityException {
    String changeType = getChangeType(commit);
    if (changeType.equals("DELETE")) {
      return "File deleted";
    } else
      return commit.getMsg();
  }

  private String getCommitType(ChangeLogSet.Entry commit) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException, SecurityException {
    String changeType = getChangeType(commit);
    if ("ADD".equals(changeType)) {
      return EditType.ADD.getName();
    } else if ("DELETE".equals(changeType)) {
      return EditType.DELETE.getName();
    } else if ("MODIFIED".equals(changeType)) {
      return EditType.EDIT.getName();
    }
    return null;
  }

}
