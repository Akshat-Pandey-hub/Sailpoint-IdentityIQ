Place the SailPoint IdentityIQ compile-time JARs here (copied from your IIQ server):

  <TOMCAT>/webapps/identityiq/WEB-INF/lib/identityiq.jar        -> ./identityiq.jar   (REQUIRED)
  <TOMCAT>/webapps/identityiq/WEB-INF/lib/connector-bundle.jar  -> ./connector-bundle.jar (optional)

These are COMPILE-TIME ONLY. The IIQ runtime supplies sailpoint.* classes at execution time,
so they are declared 'system'/'provided' scope in pom.xml and are NOT bundled into the output jar.

They are intentionally NOT committed to this repository (proprietary SailPoint artifacts).
