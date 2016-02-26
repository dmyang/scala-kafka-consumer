resolvers := Seq(
  "artifactory-pagerduty-maven" at "https://pagerduty.artifactoryonline.com/pagerduty/all/",
  Resolver.url( // Add Artifactory again, this time treating it as an ivy repository
    "artifactory-pagerduty-ivy",
    url("https://pagerduty.artifactoryonline.com/pagerduty/all/")
  )(Resolver.ivyStylePatterns),
  Resolver.defaultLocal)

publishTo := Some(
  Resolver.url(
    "artifactory-pagerduty-releases",
    url("https://pagerduty.artifactoryonline.com/pagerduty/pd-releases/")
  )(Resolver.ivyStylePatterns))

publishMavenStyle := false
