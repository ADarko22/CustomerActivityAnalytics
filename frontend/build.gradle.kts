plugins {
    alias(libs.plugins.node)
}

node {
    version.set("22.12.0")
    download.set(true)
    nodeProjectDir.set(file("$projectDir"))
}