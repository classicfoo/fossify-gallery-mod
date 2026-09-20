package org.fossify.gallery.adapters

import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.models.Medium

class MyPagerAdapter(val activity: ViewPagerActivity, fm: FragmentManager, val media: MutableList<Medium>) : FragmentStatePagerAdapter(fm) {
    private val fragments = HashMap<Int, ViewPagerFragment>()
    var shouldInitFragment = true

    override fun getCount() = media.size

    override fun getItem(position: Int): Fragment {
        val medium = media[position]
        val bundle = Bundle()
        bundle.putSerializable(MEDIUM, medium)
        bundle.putBoolean(SHOULD_INIT_FRAGMENT, shouldInitFragment)
        val fragment = if (medium.isVideo()) {
            VideoFragment()
        } else {
            PhotoFragment()
        }

        fragment.arguments = bundle
        return fragment
    }

    override fun getItemPosition(item: Any): Int {
        val oldMedium = (item as? ViewPagerFragment)?.arguments?.getSerializable(MEDIUM) as? Medium
            ?: return PagerAdapter.POSITION_NONE
        val position = media.indexOfFirst { sameMedium(it, oldMedium) }
        if (position < 0) {
            return PagerAdapter.POSITION_NONE
        }

        val current = media[position]
        return if (current.path == oldMedium.path && current.getSignature() == oldMedium.getSignature()) {
            position
        } else {
            PagerAdapter.POSITION_NONE
        }
    }

    fun updateMedia(newMedia: Collection<Medium>) {
        val existingFragments = fragments.values.toList()
        media.clear()
        media.addAll(newMedia)
        fragments.clear()
        existingFragments.forEach { fragment ->
            val medium = (fragment.arguments?.getSerializable(MEDIUM) as? Medium) ?: return@forEach
            val position = media.indexOfFirst { sameMedium(it, medium) }
            if (position >= 0) {
                fragments[position] = fragment
            }
        }
        notifyDataSetChanged()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as ViewPagerFragment

        // getItem() might not be called if the activity is recreated, so the listener must be set here
        fragment.listener = activity

        fragments[position] = fragment
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        fragments.remove(position)
        super.destroyItem(container, position, any)
    }

    fun getCurrentFragment(position: Int) = fragments[position]

    private fun sameMedium(first: Medium, second: Medium): Boolean {
        return if (first.mediaStoreId != 0L && second.mediaStoreId != 0L) {
            first.mediaStoreId == second.mediaStoreId
        } else {
            first.path.equals(second.path, true)
        }
    }

    fun toggleFullscreen(isFullscreen: Boolean) {
        for ((pos, fragment) in fragments) {
            fragment.fullscreenToggled(isFullscreen)
        }
    }

    // try fixing TransactionTooLargeException crash on Android Nougat, tip from https://stackoverflow.com/a/43193425/1967672
    override fun saveState(): Parcelable? {
        val bundle = super.saveState() as Bundle?
        bundle?.putParcelableArray("states", null)
        return bundle
    }
}
