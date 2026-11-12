plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.cache)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.invtx)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.registry)
    implementation(projects.api.repo)
    implementation(projects.content.interfaces.bank)
}
