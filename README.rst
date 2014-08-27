fogbugz-plugin for Jenkins
==========================

A Jenkins plugin to integrate Fogbugz and Jenkins.
Supports the following operations:

- Recognizing and providing links to cases in SCM log (since 1.0)
- Creating a new case in Fogbugz if the build starts to fail (since 2.0)
- Poll Fogbuz for cases to be triggered builds with
- Reporting build status to Fogbugz by editing the case and posting a message * (since 2.0)
- Creating a build action so a link to the Fogbugz case this build was triggered from / belongs to is shown * (since 2.0)

Operations marked with an asterisk (*) need the CASE_ID string parameter on a Build so the plugin knows which
case it belongs to.
If you trigger a build using the URLTrigger from Fogbugz, this is done automatically, so long as you configure
the trigger like mentioned in URLTRIGGER.rst.

.. image::
  https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/fogbugz-plugin
  :target: https://jenkins.ci.cloudbees.com/job/plugins/job/fogbugz-plugin/
