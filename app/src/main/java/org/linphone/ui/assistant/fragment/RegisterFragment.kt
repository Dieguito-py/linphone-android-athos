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
package org.linphone.ui.assistant.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantRegisterFragmentBinding
import org.linphone.ui.assistant.model.ConfirmPhoneNumberDialogModel
import org.linphone.ui.assistant.viewmodel.AccountCreationViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.PhoneNumberUtils

@UiThread
class RegisterFragment : Fragment() {
    companion object {
        private const val TAG = "[Register Fragment]"
    }

    private lateinit var binding: AssistantRegisterFragmentBinding

    private val viewModel: AccountCreationViewModel by navGraphViewModels(
        R.id.registerFragment
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantRegisterFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        binding.setLoginClickListener {
            goBack()
        }

        binding.setShowCountryPickerClickListener {
            val countryPickerFragment = CountryPickerFragment()
            countryPickerFragment.listener = viewModel
            countryPickerFragment.show(childFragmentManager, "CountryPicker")
        }

        binding.setOpenSubscribeWebPageClickListener {
            try {
                val url = "https://subscribe.linphone.org"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Can't start ACTION_VIEW intent, IllegalStateException: $ise")
            }
        }

        binding.username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.usernameError.value = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.phoneNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.phoneNumberError.value = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewModel.normalizedPhoneNumberEvent.observe(viewLifecycleOwner) {
            it.consume { number ->
                showPhoneNumberConfirmationDialog(number)
            }
        }

        viewModel.showPassword.observe(viewLifecycleOwner) {
            lifecycleScope.launch {
                delay(50)
                binding.password.setSelection(binding.password.text?.length ?: 0)
            }
        }

        viewModel.goToSmsCodeConfirmationViewEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Going to SMS code confirmation fragment")
                val action = RegisterFragmentDirections.actionRegisterFragmentToRegisterCodeConfirmationFragment()
                findNavController().navigate(action)
            }
        }

        coreContext.postOnCoreThread {
            val prefix = PhoneNumberUtils.getDeviceInternationalPrefix(requireContext())
            if (!prefix.isNullOrEmpty()) {
                viewModel.internationalPrefix.postValue("+$prefix")
            }
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }

    private fun showPhoneNumberConfirmationDialog(number: String) {
        val model = ConfirmPhoneNumberDialogModel(number)
        val dialog = DialogUtils.getAccountCreationPhoneNumberConfirmationDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmPhoneNumberEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.requestToken()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
