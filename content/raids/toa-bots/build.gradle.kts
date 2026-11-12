plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.raids.toa)
    implementation(projects.api.bosses)
    implementation(projects.api.account)
    implementation(projects.api.net)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.registry)
    implementation(projects.api.script)
    implementation(projects.api.pluginCommons)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
}
