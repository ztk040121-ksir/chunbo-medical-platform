package com.chunbo.medical.ui.common

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.R
import com.chunbo.medical.databinding.DialogRegionCascadePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 仿主流电商（京东/淘宝）三级级联省市区选择器：
 * 1. 省份 -> 2. 城市 -> 3. 区/县
 * 彻底消除单一大列表翻找的糟糕体验，支持顶栏面包屑随时回退切换，以及手动精确输入。
 */
object RegionPickerHelper {

    // 常用全国省市区行政区划数据映射表
    private val REGION_DATA: Map<String, Map<String, List<String>>> = mapOf(
        "湖南省" to mapOf(
            "长沙市" to listOf("岳麓区", "芙蓉区", "天心区", "开福区", "雨花区", "望城区", "长沙县", "浏阳市", "宁乡市"),
            "株洲市" to listOf("天元区", "荷塘区", "芦淞区", "石峰区", "渌口区", "醴陵市", "攸县", "茶陵县", "炎陵县"),
            "湘潭市" to listOf("雨湖区", "岳塘区", "湘乡市", "韶山市", "湘潭县"),
            "衡阳市" to listOf("雁峰区", "石鼓区", "珠晖区", "蒸湘区", "南岳区", "耒阳市", "常宁市", "衡阳县", "衡南县", "衡山县"),
            "邵阳市" to listOf("双清区", "大祥区", "北塔区", "武冈市", "邵东市", "新邵县", "邵阳县", "隆回县", "洞口县"),
            "岳阳市" to listOf("岳阳楼区", "云溪区", "君山区", "汨罗市", "临湘市", "岳阳县", "华容县", "湘阴县", "平江县"),
            "常德市" to listOf("武陵区", "鼎城区", "津市市", "安乡县", "汉寿县", "澧县", "临澧县", "桃源县", "石门县"),
            "益阳市" to listOf("赫山区", "资阳区", "沅江市", "南县", "桃江县", "安化县"),
            "郴州市" to listOf("北湖区", "苏仙区", "资兴市", "桂阳县", "宜章县", "永兴县", "嘉禾县", "临武县", "汝城县"),
            "永州市" to listOf("零陵区", "冷水滩区", "祁阳市", "东安县", "双牌县", "道县", "江永县", "宁远县", "蓝山县"),
            "怀化市" to listOf("鹤城区", "洪江市", "中方县", "沅陵县", "辰溪县", "溆浦县", "会同县", "麻阳县", "芷江县"),
            "娄底市" to listOf("娄星区", "冷水江市", "涟源市", "双峰县", "新化县"),
            "张家界市" to listOf("永定区", "武陵源区", "慈利县", "桑植县"),
            "湘西州" to listOf("吉首市", "泸溪县", "凤凰县", "花垣县", "保靖县", "古丈县", "永顺县", "龙山县")
        ),
        "广东省" to mapOf(
            "广州市" to listOf("越秀区", "天河区", "海珠区", "荔湾区", "白云区", "黄埔区", "番禺区", "花都区", "南沙区", "从化区", "增城区"),
            "深圳市" to listOf("南山区", "福田区", "罗湖区", "宝安区", "龙岗区", "龙华区", "盐田区", "坪山区", "光明区", "大鹏新区"),
            "珠海市" to listOf("香洲区", "斗门区", "金湾区"),
            "佛山市" to listOf("禅城区", "南海区", "顺德区", "三水区", "高明区"),
            "东莞市" to listOf("南城街道", "东城街道", "莞城街道", "万江街道", "长安镇", "虎门镇", "塘厦镇", "松山湖高新区"),
            "中山市" to listOf("石岐街道", "东区街道", "中山港街道", "小榄镇", "火炬开发区", "三乡镇", "坦洲镇"),
            "惠州市" to listOf("惠城区", "惠阳区", "惠东县", "博罗县", "龙门县")
        ),
        "北京市" to mapOf(
            "北京市" to listOf("朝阳区", "海淀区", "东城区", "西城区", "丰台区", "石景山区", "通州区", "顺义区", "昌平区", "大兴区", "房山区", "门头沟区")
        ),
        "上海市" to mapOf(
            "上海市" to listOf("浦东新区", "黄浦区", "徐汇区", "长宁区", "静安区", "普陀区", "虹口区", "杨浦区", "闵行区", "宝山区", "嘉定区", "松江区", "青浦区")
        ),
        "湖北省" to mapOf(
            "武汉市" to listOf("武昌区", "洪山区", "江岸区", "江汉区", "硚口区", "汉阳区", "青山区", "东西湖区", "蔡甸区", "江夏区", "黄陂区"),
            "宜昌市" to listOf("西陵区", "伍家岗区", "点军区", "猇亭区", "夷陵区", "宜都市", "当阳市", "枝江市"),
            "襄阳市" to listOf("襄城区", "樊城区", "襄州区", "老河口市", "枣阳市", "宜城市"),
            "荆州市" to listOf("沙市区", "荆州区", "松滋市", "石首市", "洪湖市", "监利市")
        ),
        "浙江省" to mapOf(
            "杭州市" to listOf("西湖区", "上城区", "拱墅区", "滨江区", "萧山区", "余杭区", "临平区", "钱塘区", "富阳区", "临安区"),
            "宁波市" to listOf("海曙区", "江北区", "镇海区", "北仑区", "鄞州区", "奉化区", "余姚市", "慈溪市"),
            "温州市" to listOf("鹿城区", "龙湾区", "瓯海区", "洞头区", "瑞安市", "乐清市")
        ),
        "江苏省" to mapOf(
            "南京市" to listOf("玄武区", "秦淮区", "建邺区", "鼓楼区", "浦口区", "栖霞区", "雨花台区", "江宁区", "六合区"),
            "苏州市" to listOf("姑苏区", "虎丘区", "吴中区", "相城区", "吴江区", "昆山市", "常熟市", "张家港市"),
            "无锡市" to listOf("梁溪区", "锡山区", "惠山区", "滨湖区", "新吴区", "江阴市", "宜兴市")
        ),
        "四川省" to mapOf(
            "成都市" to listOf("武侯区", "锦江区", "青羊区", "金牛区", "成华区", "高新区", "天府新区", "双流区", "温江区", "郫都区", "新都区"),
            "绵阳市" to listOf("涪城区", "游仙区", "安州区", "江油市")
        ),
        "重庆市" to mapOf(
            "重庆市" to listOf("渝中区", "江北区", "南岸区", "沙坪坝区", "九龙坡区", "大渡口区", "渝北区", "巴南区", "北碚区", "璧山区")
        ),
        "山东省" to mapOf(
            "济南市" to listOf("历下区", "市中区", "槐荫区", "天桥区", "历城区", "长清区", "高新区"),
            "青岛市" to listOf("市南区", "市北区", "李沧区", "崂山区", "黄岛区", "城阳区", "即墨区")
        ),
        "河南省" to mapOf(
            "郑州市" to listOf("金水区", "中原区", "二七区", "管城区", "惠济区", "郑东新区", "高新区", "中牟县"),
            "洛阳市" to listOf("涧西区", "西工区", "老城区", "瀍河区", "洛龙区")
        ),
        "陕西省" to mapOf(
            "西安市" to listOf("雁塔区", "碑林区", "莲湖区", "新城区", "未央区", "灞桥区", "长安区", "高新区", "曲江新区")
        ),
        "江西省" to mapOf(
            "南昌市" to listOf("红谷滩区", "东湖区", "西湖区", "青云谱区", "青山湖区", "新建区", "南昌县")
        ),
        "福建省" to mapOf(
            "福州市" to listOf("鼓楼区", "台江区", "仓山区", "晋安区", "马尾区", "长乐区", "闽侯县"),
            "厦门市" to listOf("思明区", "湖里区", "集美区", "海沧区", "同安区", "翔安区")
        ),
        "安徽省" to mapOf(
            "合肥市" to listOf("蜀山区", "包河区", "庐阳区", "瑶海区", "经开区", "高新区", "肥西县")
        )
    )

    /** 显示三级级联选择器弹窗 */
    fun showRegionPicker(context: Context, currentRegion: String, onSelected: (String) -> Unit) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogRegionCascadePickerBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        var selectedProvince = ""
        var selectedCity = ""
        var selectedDistrict = ""

        // 解析已有地址预填
        val segments = currentRegion.replace(Regex("\\s*\\([^)]*\\)"), "").trim().split(Regex("\\s+"))
        if (segments.isNotEmpty() && REGION_DATA.containsKey(segments[0])) {
            selectedProvince = segments[0]
            val cities = REGION_DATA[selectedProvince]
            if (segments.size >= 2 && cities != null && cities.containsKey(segments[1])) {
                selectedCity = segments[1]
                val districts = cities[selectedCity]
                if (segments.size >= 3 && districts != null && districts.contains(segments[2])) {
                    selectedDistrict = segments[2]
                }
            }
        } else {
            // 默认首选湖南省
            selectedProvince = "湖南省"
        }

        // 当前步骤：1=选省, 2=选市, 3=选区/县
        var currentStep = if (selectedProvince.isNotEmpty() && selectedCity.isNotEmpty()) 3 else (if (selectedProvince.isNotEmpty()) 2 else 1)

        val adapter = RegionChoiceAdapter { pickedName ->
            when (currentStep) {
                1 -> {
                    selectedProvince = pickedName
                    selectedCity = ""
                    selectedDistrict = ""
                    currentStep = 2
                    renderUI(binding, currentStep, selectedProvince, selectedCity, selectedDistrict)
                }
                2 -> {
                    selectedCity = pickedName
                    selectedDistrict = ""
                    currentStep = 3
                    renderUI(binding, currentStep, selectedProvince, selectedCity, selectedDistrict)
                }
                3 -> {
                    selectedDistrict = pickedName
                    val fullRegion = "$selectedProvince $selectedCity $selectedDistrict"
                    onSelected(fullRegion)
                    dialog.dismiss()
                }
            }
        }

        binding.rvRegionList.layoutManager = LinearLayoutManager(context)
        binding.rvRegionList.adapter = adapter

        // 面包屑 Tab 点击事件（支持随时点回上一级重新选择）
        binding.tabProvince.setOnClickListener {
            currentStep = 1
            renderUI(binding, currentStep, selectedProvince, selectedCity, selectedDistrict)
        }
        binding.tabCity.setOnClickListener {
            if (selectedProvince.isNotEmpty()) {
                currentStep = 2
                renderUI(binding, currentStep, selectedProvince, selectedCity, selectedDistrict)
            }
        }
        binding.tabDistrict.setOnClickListener {
            if (selectedCity.isNotEmpty()) {
                currentStep = 3
                renderUI(binding, currentStep, selectedProvince, selectedCity, selectedDistrict)
            }
        }

        binding.btnClosePicker.setOnClickListener { dialog.dismiss() }

        binding.btnManualInput.setOnClickListener {
            dialog.dismiss()
            showCustomRegionInput(context, currentRegion, onSelected)
        }

        renderUI(binding, currentStep, selectedProvince, selectedCity, selectedDistrict)
        dialog.show()
    }

    private fun renderUI(
        binding: DialogRegionCascadePickerBinding,
        step: Int,
        province: String,
        city: String,
        district: String
    ) {
        val primaryColor = binding.root.context.getColor(R.color.primary)
        val mutedColor = binding.root.context.getColor(R.color.text_muted)

        // 1. 省份 Tab
        if (province.isEmpty()) {
            binding.tabProvince.text = "请选择省份"
            binding.tabProvince.setTextColor(primaryColor)
            binding.arrow1.visibility = View.GONE
            binding.tabCity.visibility = View.GONE
            binding.arrow2.visibility = View.GONE
            binding.tabDistrict.visibility = View.GONE
        } else {
            binding.tabProvince.text = province
            binding.tabProvince.setTextColor(if (step == 1) primaryColor else mutedColor)
            binding.arrow1.visibility = View.VISIBLE
            binding.tabCity.visibility = View.VISIBLE
        }

        // 2. 城市 Tab
        if (city.isEmpty()) {
            binding.tabCity.text = "请选择城市"
            binding.tabCity.setTextColor(primaryColor)
            binding.arrow2.visibility = View.GONE
            binding.tabDistrict.visibility = View.GONE
        } else {
            binding.tabCity.text = city
            binding.tabCity.setTextColor(if (step == 2) primaryColor else mutedColor)
            binding.arrow2.visibility = View.VISIBLE
            binding.tabDistrict.visibility = View.VISIBLE
        }

        // 3. 区县 Tab
        if (district.isEmpty()) {
            binding.tabDistrict.text = "请选择区/县"
            binding.tabDistrict.setTextColor(primaryColor)
        } else {
            binding.tabDistrict.text = district
            binding.tabDistrict.setTextColor(if (step == 3) primaryColor else mutedColor)
        }

        // 加载对应步骤的数据列表
        val adapter = binding.rvRegionList.adapter as? RegionChoiceAdapter ?: return
        when (step) {
            1 -> {
                val list = REGION_DATA.keys.toList()
                adapter.setData(list, province)
            }
            2 -> {
                val cities = REGION_DATA[province]?.keys?.toList() ?: emptyList()
                adapter.setData(cities, city)
            }
            3 -> {
                val districts = REGION_DATA[province]?.get(city) ?: emptyList()
                adapter.setData(districts, district)
            }
        }
    }

    private fun showCustomRegionInput(context: Context, oldText: String, onSelected: (String) -> Unit) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }
        val et = EditText(context).apply {
            hint = "格式示例: 湖南省 长沙市 岳麓区"
            setText(oldText)
        }
        layout.addView(et)

        AlertDialog.Builder(context)
            .setTitle("✏️ 手动输入省/市/区县")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val text = et.text.toString().trim()
                if (text.isNotEmpty()) {
                    onSelected(text)
                }
            }
            .setNegativeButton("返回级联选择") { _, _ ->
                showRegionPicker(context, oldText, onSelected)
            }
            .show()
    }

    /** 从完整地址中智能拆分：省市区 与 详细门牌 */
    fun splitAddress(fullAddress: String?): Pair<String, String> {
        if (fullAddress.isNullOrBlank()) {
            return Pair("湖南省 长沙市 岳麓区", "")
        }
        val trimmed = fullAddress.trim()
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size >= 4) {
            val reg = "${parts[0]} ${parts[1]} ${parts[2]}"
            val detail = parts.drop(3).joinToString(" ")
            return Pair(reg, detail)
        }
        if (parts.size == 3 && (parts[0].contains("省") || parts[0].contains("市")) && (parts[1].contains("市") || parts[1].contains("区"))) {
            return Pair("${parts[0]} ${parts[1]} ${parts[2]}", "")
        }
        if (parts.size == 2) {
            return Pair(parts[0], parts[1])
        }
        return Pair("湖南省 长沙市 岳麓区", trimmed)
    }

    private class RegionChoiceAdapter(
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<RegionChoiceAdapter.ViewHolder>() {

        private val items = mutableListOf<String>()
        private var selectedItem: String = ""

        fun setData(newItems: List<String>, selected: String) {
            items.clear()
            items.addAll(newItems)
            selectedItem = selected
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_region_choice, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val name = items[position]
            val isChecked = name == selectedItem
            holder.tvName.text = name
            holder.tvName.setTextColor(
                if (isChecked) holder.itemView.context.getColor(R.color.primary)
                else holder.itemView.context.getColor(R.color.text_primary)
            )
            holder.tvChecked.visibility = if (isChecked) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                onItemClick(name)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tv_choice_name)
            val tvChecked: TextView = itemView.findViewById(R.id.tv_choice_checked)
        }
    }
}
