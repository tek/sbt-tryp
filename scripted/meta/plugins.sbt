val tv = sys.props.get("tryp.version")
    .getOrElse(sys.error("need to pass -Dtryp.version"))
addSbtPlugin("tryp.sbt" % "tryp-android" % tv)
