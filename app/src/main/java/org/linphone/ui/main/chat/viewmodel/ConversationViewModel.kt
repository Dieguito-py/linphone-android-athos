/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.chat.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.model.EventLogModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.GroupAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Conversation ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val groupAvatarModel = MutableLiveData<GroupAvatarModel>()

    val events = MutableLiveData<ArrayList<EventLogModel>>()

    val isGroup = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val isReadOnly = MutableLiveData<Boolean>()

    val composingLabel = MutableLiveData<String>()

    val textToSend = MutableLiveData<String>()

    val chatRoomFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var chatRoom: ChatRoom

    private val avatarsMap = hashMapOf<String, ContactAvatarModel>()

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Chat message [$message] is being sent")

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())

            val avatarModel = getAvatarModelForAddress(message?.localAddress)
            list.add(EventLogModel(eventLog, avatarModel))

            events.postValue(list)
        }

        @WorkerThread
        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            val message = eventLog.chatMessage
            Log.i("$TAG Chat message [$message] has been sent")
        }

        @WorkerThread
        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            isComposing: Boolean
        ) {
            Log.i(
                "$TAG Remote [${remoteAddress.asStringUriOnly()}] is ${if (isComposing) "composing" else "no longer composing"}"
            )
            computeComposingLabel()
        }

        @WorkerThread
        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<out EventLog>) {
            Log.i("$TAG Received [${eventLogs.size}] new message(s)")
            chatRoom.markAsRead()
            computeComposingLabel()

            val list = arrayListOf<EventLogModel>()
            list.addAll(events.value.orEmpty())

            for (eventLog in eventLogs) {
                val address = if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                    eventLog.chatMessage?.fromAddress
                } else {
                    eventLog.participantAddress
                }
                val avatarModel = getAvatarModelForAddress(address)
                list.add(EventLogModel(eventLog, avatarModel))
            }

            events.postValue(list)
        }
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            chatRoom.removeListener(chatRoomListener)
            events.value.orEmpty().forEach(EventLogModel::destroy)
            avatarsMap.values.forEach(ContactAvatarModel::destroy)
        }
    }

    @UiThread
    fun findChatRoom(localSipUri: String, remoteSipUri: String) {
        coreContext.postOnCoreThread { core ->
            Log.i(
                "$TAG Looking for chat room with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteAddress = Factory.instance().createAddress(remoteSipUri)
            if (localAddress != null && remoteAddress != null) {
                val found = core.searchChatRoom(
                    null,
                    localAddress,
                    remoteAddress,
                    arrayOfNulls(
                        0
                    )
                )
                if (found != null) {
                    chatRoom = found
                    chatRoom.addListener(chatRoomListener)

                    configureChatRoom()
                    chatRoomFoundEvent.postValue(Event(true))
                } else {
                    Log.e("Failed to find chat room given local & remote addresses!")
                    chatRoomFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun sendMessage() {
        coreContext.postOnCoreThread { core ->
            val message = chatRoom.createEmptyMessage()

            val toSend = textToSend.value.orEmpty().trim()
            if (toSend.isNotEmpty()) {
                message.addUtf8TextContent(toSend)
            }

            if (message.contents.isNotEmpty()) {
                Log.i("$TAG Sending message")
                message.send()
            }
            textToSend.postValue("")
        }
    }

    @UiThread
    fun deleteChatMessage(chatMessageModel: ChatMessageModel) {
        coreContext.postOnCoreThread {
            val eventsLogs = events.value.orEmpty()
            val found = eventsLogs.find {
                it.model == chatMessageModel
            }
            if (found != null) {
                val list = arrayListOf<EventLogModel>()
                list.addAll(eventsLogs)
                list.remove(found)
                events.postValue(list)
            }

            Log.i("$TAG Deleting message id [${chatMessageModel.id}]")
            chatRoom.deleteMessage(chatMessageModel.chatMessage)
        }
    }

    @WorkerThread
    private fun configureChatRoom() {
        computeComposingLabel()

        isGroup.postValue(
            !chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt()) && chatRoom.hasCapability(
                ChatRoom.Capabilities.Conference.toInt()
            )
        )

        val empty = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt()) && chatRoom.participants.isEmpty()
        val readOnly = chatRoom.isReadOnly || empty
        isReadOnly.postValue(readOnly)
        if (readOnly) {
            Log.w("$TAG Chat room with subject [${chatRoom.subject}] is read only!")
        }

        subject.postValue(chatRoom.subject)

        val friends = arrayListOf<Friend>()
        val address = if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            chatRoom.peerAddress
        } else {
            for (participant in chatRoom.participants) {
                val friend = coreContext.contactsManager.findContactByAddress(participant.address)
                if (friend != null) {
                    friends.add(friend)
                }
            }

            val firstParticipant = chatRoom.participants.firstOrNull()
            firstParticipant?.address ?: chatRoom.peerAddress
        }
        val avatar = getAvatarModelForAddress(address)
        avatarModel.postValue(avatar)
        val groupAvatar = GroupAvatarModel(friends)
        groupAvatarModel.postValue(groupAvatar)

        val eventsList = arrayListOf<EventLogModel>()

        val history = chatRoom.getHistoryEvents(0)
        for (event in history) {
            val avatar = getAvatarModelForAddress(event.chatMessage?.fromAddress)
            val model = EventLogModel(event, avatar)
            eventsList.add(model)
        }

        events.postValue(eventsList)
        chatRoom.markAsRead()
    }

    @WorkerThread
    private fun getAvatarModelForAddress(address: Address?): ContactAvatarModel {
        Log.i("Looking for avatar model with address [${address?.asStringUriOnly()}]")
        if (address == null) {
            val fakeFriend = coreContext.core.createFriend()
            return ContactAvatarModel(fakeFriend)
        }

        val clone = address.clone()
        clone.clean()
        val key = clone.asStringUriOnly()

        val foundInMap = if (avatarsMap.keys.contains(key)) avatarsMap[key] else null
        if (foundInMap != null) return foundInMap

        val friend = coreContext.contactsManager.findContactByAddress(clone)
        val avatar = if (friend != null) {
            ContactAvatarModel(friend)
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = clone
            ContactAvatarModel(fakeFriend)
        }

        avatarsMap[key] = avatar
        return avatar
    }

    @WorkerThread
    private fun computeComposingLabel() {
        var composingFriends = arrayListOf<String>()
        var label = ""
        for (address in chatRoom.composingAddresses) {
            val avatar = getAvatarModelForAddress(address)
            val name = avatar.name.value ?: LinphoneUtils.getDisplayName(address)
            composingFriends.add(name)
            label += "$name, "
        }
        if (composingFriends.size > 0) {
            label = label.dropLast(2)

            val format = AppUtils.getStringWithPlural(
                R.plurals.conversation_composing_label,
                composingFriends.size,
                label
            )
            composingLabel.postValue(format)
        } else {
            composingLabel.postValue("")
        }
    }
}