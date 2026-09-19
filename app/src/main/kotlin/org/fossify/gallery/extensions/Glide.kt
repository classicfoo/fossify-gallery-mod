package org.fossify.gallery.extensions

import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.TransitionFactory

/**
 * Cross fade transition option that disabled fading when loading from cache.
 */
fun getOptionalCrossFadeTransition(duration: Int): DrawableTransitionOptions {
    if (duration <= 0) return DrawableTransitionOptions().dontTransition()
    return DrawableTransitionOptions.with(
        TransitionFactory { dataSource, isFirstResource ->
            if (dataSource == DataSource.RESOURCE_DISK_CACHE) return@TransitionFactory null
            DrawableCrossFadeFactory.Builder(duration).build().build(dataSource, isFirstResource)
        }
    )
}
