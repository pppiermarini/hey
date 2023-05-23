// Licensed under MIT
// author : Damien Nozay

// list jobs and their last build.

jobs = []
jobs.add(Jenkins.instance.getItem('ADD JOB NAME HERE'))
// jobs.add(Jenkins.instance.getItem('REPEAT LINE FOR ALL JOBS'))

jobs.each { j ->
  if (j instanceof com.cloudbees.hudson.plugins.folder.Folder) { return }
  if (j instanceof org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject) { return }
  if (j instanceof jenkins.branch.OrganizationFolder) { return }
  println 'JOB: ' + j.fullName
  numbuilds = j.builds.size()
  if (numbuilds == 0) {
    println '  -> no build'
    return
  }
  lastbuild = j.builds[numbuilds - 1]
    println '  -> lastbuild: ' + lastbuild.displayName + ' = ' + lastbuild.result + ', time: ' + lastbuild.timestampString2
}

// returns blank
''