package dev.gaferneira.notificapp.features.ruledetails.domain

import dev.gaferneira.notificapp.domain.model.Rule

/**
 * A section (When / Do) with more than this many entries is considered "long" and starts collapsed
 * on the details screen; the summary card above it already gives the gist. Three entries still fit
 * comfortably on screen, a fourth starts pushing the rest of the page away.
 */
const val LONG_SECTION_THRESHOLD = 3

/** Whether the When / Do detail sections start collapsed for a given rule. */
data class SectionCollapseDefaults(val whenCollapsed: Boolean, val doCollapsed: Boolean)

/**
 * Collapse When when the rule has more than [LONG_SECTION_THRESHOLD] conditions and Do when it has
 * more than [LONG_SECTION_THRESHOLD] actions. Disabled actions count: they are still listed in the
 * section. Short rules stay fully expanded. Users can always toggle either section afterwards.
 */
fun Rule.sectionCollapseDefaults(): SectionCollapseDefaults = SectionCollapseDefaults(
    whenCollapsed = conditions.size > LONG_SECTION_THRESHOLD,
    doCollapsed = actions.size > LONG_SECTION_THRESHOLD,
)
