package com.kurly.loupe.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.kurly.loupe.DesignInspector
import com.kurly.loupe.R

/**
 * XML 기반 화면에서 디버그 설정으로 사용할 수 있는 Fragment.
 *
 * 플로팅 토글 버튼의 표시 여부를 제어합니다.
 *
 * 사용법:
 * ```xml
 * <fragment
 *     android:name="com.kurly.loupe.ui.InspectorToggleFragment"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 */
class InspectorToggleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.view_inspector_toggle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val card = view.findViewById<MaterialCardView>(R.id.card_inspector)
        val switch = view.findViewById<MaterialSwitch>(R.id.switch_inspector)
        val desc = view.findViewById<TextView>(R.id.tv_inspector_desc)
        val legend = view.findViewById<View>(R.id.legend_container)

        // 현재 상태 반영
        switch.isChecked = DesignInspector.isToggleShown
        updateUI(switch.isChecked, card, desc, legend)

        switch.setOnCheckedChangeListener { _, isChecked ->
            val activity = requireActivity()
            if (isChecked) {
                DesignInspector.showToggle(activity)
            } else {
                DesignInspector.hideToggle()
            }
            updateUI(isChecked, card, desc, legend)
        }
    }

    private fun updateUI(
        isEnabled: Boolean,
        card: MaterialCardView,
        desc: TextView,
        legend: View,
    ) {
        if (isEnabled) {
            card.setCardBackgroundColor(0x145F0080) // purple with low alpha
            desc.text = "플로팅 버튼으로 인스펙터를 켜고 끌 수 있습니다"
            legend.visibility = View.VISIBLE
        } else {
            card.setCardBackgroundColor(0xFFF5F5F5.toInt())
            desc.text = "플로팅 토글 버튼을 화면에 표시합니다"
            legend.visibility = View.GONE
        }
    }
}