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
    // Positions are not stable when a medium is deleted. Keep the live fragments by identity and
    // resolve the current one against the current media list instead of retaining old positions.
    private val fragments = LinkedHashSet<ViewPagerFragment>()
    var shouldInitFragment = true

    override fun getCount() = media.size

    override fun getItem(position: Int): Fragment {
        val medium = media.getOrNull(position) ?: return Fragment()
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
        return if (current.path.equals(oldMedium.path, true) && current.getSignature() == oldMedium.getSignature()) {
            position
        } else {
            PagerAdapter.POSITION_NONE
        }
    }

    fun updateMedia(newMedia: Collection<Medium>) {
        media.clear()
        media.addAll(newMedia)
        notifyDataSetChanged()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = super.instantiateItem(container, position)
        val fragment = item as? ViewPagerFragment ?: return item

        // getItem() might not be called if the activity is recreated, so the listener must be set here
        fragment.listener = activity

        fragments.add(fragment)
        return item
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        (any as? ViewPagerFragment)?.let { fragments.remove(it) }
        super.destroyItem(container, position, any)
    }

    fun getCurrentFragment(position: Int): ViewPagerFragment? {
        val target = media.getOrNull(position) ?: return null
        return fragments.firstOrNull { fragment ->
            val medium = fragment.arguments?.getSerializable(MEDIUM) as? Medium
            medium != null && sameMedium(medium, target)
        }
    }

    private fun sameMedium(first: Medium, second: Medium): Boolean {
        return if (first.mediaStoreId != 0L && second.mediaStoreId != 0L) {
            first.mediaStoreId == second.mediaStoreId
        } else {
            first.path.equals(second.path, true)
        }
    }

    fun toggleFullscreen(isFullscreen: Boolean) {
        for (fragment in fragments) {
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
