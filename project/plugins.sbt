resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
    Resolver.ivyStylePatterns)

//addSbtPlugin("me.lessis" % "bintray-sbt" % "0.2.1")

//addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.5")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.0")

//addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.4")

//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.9.0")

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")