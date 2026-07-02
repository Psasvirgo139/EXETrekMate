package com.trekmate.app.core.network.sse

import com.trekmate.app.core.network.dto.TourMemberDto

sealed class TourSseEvent {
    /** Server pushed a new member list (someone joined) */
    data class MemberUpdate(val members: List<TourMemberDto>) : TourSseEvent()

    /** Leader ended the tour — device should clear local tour and go Home */
    object TourEnded : TourSseEvent()

    /** Connection lost — client will auto-reconnect */
    object Disconnected : TourSseEvent()
}
