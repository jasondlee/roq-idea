package com.steeplesoft.intelliroq.icons

import com.intellij.openapi.util.IconLoader

/**
 * Icons for the Roq plugin.
 */
object RoqIcons {
    @JvmField
    val RoqFile = IconLoader.getIcon("/icons/roq-file.svg", RoqIcons::class.java.classLoader)

    @JvmField
    val RoqProject = IconLoader.getIcon("/icons/roq-project.svg", RoqIcons::class.java.classLoader)

    @JvmField
    val DataFile = IconLoader.getIcon("/icons/data-file.svg", RoqIcons::class.java.classLoader)

    @JvmField
    val Template = IconLoader.getIcon("/icons/template.svg", RoqIcons::class.java.classLoader)

    @JvmField
    val Partial = IconLoader.getIcon("/icons/partial.svg", RoqIcons::class.java.classLoader)
}
