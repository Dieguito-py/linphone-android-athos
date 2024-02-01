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
package org.linphone.ui.call.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.CallConferenceGridFragmentBinding
import org.linphone.ui.call.model.ConferenceModel
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
@UiThread
class ConferenceGridFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Conference Grid Fragment]"
    }

    private lateinit var binding: CallConferenceGridFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallConferenceGridFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel
        binding.conferenceViewModel = callViewModel.conferenceModel

        callViewModel.conferenceModel.conferenceLayout.observe(viewLifecycleOwner) {
            when (it) {
                ConferenceModel.ACTIVE_SPEAKER_LAYOUT -> {
                    Log.i(
                        "$TAG Conference layout changed to active speaker, navigating to matching fragment"
                    )
                    if (findNavController().currentDestination?.id == R.id.conferenceGridFragment) {
                        findNavController().navigate(
                            R.id.action_conferenceGridFragment_to_conferenceActiveSpeakerFragment
                        )
                    }
                }
                ConferenceModel.AUDIO_ONLY_LAYOUT -> {
                    Log.i(
                        "$TAG Conference layout changed to audio only, navigating to matching fragment"
                    )
                    if (findNavController().currentDestination?.id == R.id.conferenceGridFragment) {
                        findNavController().navigate(
                            R.id.action_conferenceGridFragment_to_conferenceAudioOnlyFragment
                        )
                    }
                }
                else -> {
                }
            }
        }
    }
}
