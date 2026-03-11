package com.example.divyanyanreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.divyanyanreader.databinding.FragmentReceiveBinding

class ReceiveFragment : Fragment(R.layout.fragment_receive) {

    private var _binding: FragmentReceiveBinding? = null
    private val binding get() = _binding!!

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FileTransferService.ACTION_RECEIVER_STATUS) {
                val message = intent.getStringExtra(FileTransferService.EXTRA_STATUS) ?: return
                binding.tvReceiverStatus.text = message
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentReceiveBinding.bind(view)

        binding.tvReceiverStatus.text = getString(R.string.receive_waiting_to_start)
        binding.btnStartReceiver.setOnClickListener { startReceiverService() }
        binding.btnStopReceiver.setOnClickListener { stopReceiverService() }
    }

    private fun startReceiverService() {
        val serviceIntent = Intent(requireContext(), FileTransferService::class.java)
        ContextCompat.startForegroundService(requireContext(), serviceIntent)
        binding.tvReceiverStatus.text = getString(R.string.receive_starting)
    }

    private fun stopReceiverService() {
        val stopIntent = Intent(requireContext(), FileTransferService::class.java).apply {
            action = FileTransferService.ACTION_STOP_SERVICE
        }
        requireContext().startService(stopIntent)
        binding.tvReceiverStatus.text = getString(R.string.receive_stopping)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(FileTransferService.ACTION_RECEIVER_STATUS)
        ContextCompat.registerReceiver(
            requireContext(),
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        requireContext().unregisterReceiver(statusReceiver)
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}