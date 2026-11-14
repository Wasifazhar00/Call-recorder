/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.system.storage

/**
 * Finds recording names whose final copy has been removed by the untracked retention pass.
 * The caller uses this set to clean up user-authored recording metadata.
 */
object UntrackedCascade {

    /**
     * The subset of [deleted] that nothing is left of.
     *
     * @param deleted names the untracked pass removed a copy of, across both folders.
     * @param survivingUntracked names still present in either folder without a catalog row — the
     *        counterpart copy of a name whose other copy just expired.
     * @param cataloguedWithCopies names the catalog still holds a device or Drive copy for, read *after*
     *        the catalogued half of the sweep so an entry it dropped does not protect a dead recording.
     */
    fun orphanedNames(
        deleted: Collection<String>,
        survivingUntracked: Collection<String>,
        cataloguedWithCopies: Collection<String>,
    ): Set<String> = deleted.toSet() - survivingUntracked.toSet() - cataloguedWithCopies.toSet()
}
