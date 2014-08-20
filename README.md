Assuming you have installed Idea version idea-IU-135.1230 in /usr/local/idea
answer /usr/local/idea and 13.0 to the prompts from running 
   ./install-libs.sh

then run 

    mvn install 

It will fail with 
'''
$ mvn install
[INFO] Scanning for projects...
[INFO]                                                                         
[INFO] ------------------------------------------------------------------------
[INFO] Building SonarQube Integration for IntelliJ 1.2-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO] 
[INFO] --- buildnumber-maven-plugin:1.2:create (default) @ sonar-intellij-plugin ---
[INFO] Checking for local modifications: skipped.
[INFO] Updating project files from SCM: skipped.
[INFO] Executing: /bin/sh -c cd /ssd/home/timp/git/sonar-intellij && git rev-parse --verify HEAD
[INFO] Working directory: /ssd/home/timp/git/sonar-intellij
[INFO] Storing buildNumber: 64743823332aaac730c0f16356e3f2dba8fb00ce at timestamp: 1408552947783
[INFO] Executing: /bin/sh -c cd /ssd/home/timp/git/sonar-intellij && git rev-parse --verify HEAD
[INFO] Working directory: /ssd/home/timp/git/sonar-intellij
[INFO] Storing buildScmBranch: UNKNOWN
[INFO] 
[INFO] --- maven-enforcer-plugin:1.2:enforce (enforce) @ sonar-intellij-plugin ---
[INFO] 
[INFO] --- maven-license-plugin:1.9.0:check (enforce-license-headers) @ sonar-intellij-plugin ---
[INFO] Checking licenses...
[WARNING] Unknown file extension: /ssd/home/timp/git/sonar-intellij/src/main/java/org/sonar/ide/intellij/associate/AssociateDialog.form
[WARNING] Unknown file extension: /ssd/home/timp/git/sonar-intellij/src/main/java/org/sonar/ide/intellij/config/SonarQubeServerDialog.form
[WARNING] Unknown file extension: /ssd/home/timp/git/sonar-intellij/src/main/java/org/sonar/ide/intellij/config/SonarQubeSettingsForm.form
[INFO] 
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ sonar-intellij-plugin ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 4 resources
[INFO] Copying 1 resource
[INFO] 
[INFO] --- maven-compiler-plugin:3.0:compile (default-compile) @ sonar-intellij-plugin ---
[INFO] Changes detected - recompiling the module!
[INFO] Compiling 45 source files to /ssd/home/timp/git/sonar-intellij/target/classes
[INFO] 
[INFO] --- ideauidesigner-maven-plugin:1.0-beta-1:javac2 (default) @ sonar-intellij-plugin ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 4.330 s
[INFO] Finished at: 2014-08-20T17:42:30+01:00
[INFO] Final Memory: 31M/552M
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.codehaus.mojo:ideauidesigner-maven-plugin:1.0-beta-1:javac2 (default) on project sonar-intellij-plugin: Execution default of goal org.codehaus.mojo:ideauidesigner-maven-plugin:1.0-beta-1:javac2 failed: A required class was missing while executing org.codehaus.mojo:ideauidesigner-maven-plugin:1.0-beta-1:javac2: org/jetbrains/org/objectweb/asm/ClassVisitor
[ERROR] -----------------------------------------------------
[ERROR] realm =    plugin>org.codehaus.mojo:ideauidesigner-maven-plugin:1.0-beta-1
[ERROR] strategy = org.codehaus.plexus.classworlds.strategy.SelfFirstStrategy
[ERROR] urls[0] = file:/home/timp/.m2/repository/org/codehaus/mojo/ideauidesigner-maven-plugin/1.0-beta-1/ideauidesigner-maven-plugin-1.0-beta-1.jar
[ERROR] urls[1] = file:/home/timp/.m2/repository/com/intellij/javac2/13.0/javac2-13.0.jar
[ERROR] urls[2] = file:/home/timp/.m2/repository/org/apache/ant/ant/1.8.2/ant-1.8.2.jar
[ERROR] urls[3] = file:/home/timp/.m2/repository/org/apache/ant/ant-launcher/1.8.2/ant-launcher-1.8.2.jar
[ERROR] urls[4] = file:/home/timp/.m2/repository/com/intellij/asm4-all/13.0/asm4-all-13.0.jar
[ERROR] urls[5] = file:/home/timp/.m2/repository/com/intellij/forms_rt/13.0/forms_rt-13.0.jar
[ERROR] urls[6] = file:/home/timp/.m2/repository/jdom/jdom/1.0/jdom-1.0.jar
[ERROR] urls[7] = file:/home/timp/.m2/repository/org/codehaus/plexus/plexus-utils/1.0.5/plexus-utils-1.0.5.jar
[ERROR] Number of foreign imports: 1
[ERROR] import: Entry[import  from realm ClassRealm[project>org.codehaus.sonar-ide.intellij:sonar-intellij-plugin:1.2-SNAPSHOT, parent: ClassRealm[maven.api, parent: null]]]
[ERROR] 
[ERROR] -----------------------------------------------------: org.jetbrains.org.objectweb.asm.ClassVisitor
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/PluginContainerException


'''




