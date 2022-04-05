package edu.vt.cs.cs5254.dreamcatcher
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Log
import android.view.*
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import edu.vt.cs.cs5254.dreamcatcher.databinding.FragmentDreamDetailBinding
import edu.vt.cs.cs5254.dreamcatcher.databinding.ListItemDreamEntryBinding
import java.io.File
import java.util.*

private const val TAG = "DreamDetailFragment"
private const val ARG_DREAM_ID = "dream_id"
private val FULFILLED_BUTTON_COLOR = "#DAA520"
private val DEFERRED_BUTTON_COLOR = "#A91B0D"
private val CONCEIVED_BUTTON_COLOR = "#BC8E52"
private val REFLECTION_BUTTON_COLOR= "#1a98a7"
const val REQUEST_KEY_ADD_REFLECTION = "request_key"
const val  BUNDLE_KEY_REFLECTION_TEXT = "reflection_text"

class DreamDetailFragment : Fragment() {

    private lateinit var dreamWithEntries: DreamWithEntries
    private lateinit var photoFile: File
    private lateinit var photoUri: Uri
    private lateinit var photoLauncher: ActivityResultLauncher<Uri>

    private var string =""
    private var reflectionText = ""
    private val myString = DateFormat.format("MMM d, yyyy", Calendar.getInstance().time)

    interface Callbacks {
        fun onDreamSelected(dreamId: UUID)
    }

    private var callbacks: Callbacks? = null

    private var _binding: FragmentDreamDetailBinding? = null

    private val binding get() = _binding!!
    private val viewModel : DreamDetailViewModel by viewModels()

    private var adapter: DreamEntryAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        val dreamId: UUID = arguments?.getSerializable(ARG_DREAM_ID) as UUID
        dreamWithEntries = DreamWithEntries(Dream(), emptyList());
        viewModel.loadDreamEntry(dreamId)
        Log.d(TAG, "Dream detail fragment for dream with ID ${dreamWithEntries.dream.id}")
        setHasOptionsMenu(true)
        photoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                updatePhotoView()
            }
            requireActivity().revokeUriPermission(photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentDreamDetailBinding.inflate(inflater, container, false)

        // initialize view-binding
        val view = binding.root
        binding.dreamEntryRecyclerView.layoutManager = LinearLayoutManager(context)
        val itemTouchHelper =
            ItemTouchHelper(SwipeToDeleteCallback(adapter))
        itemTouchHelper.attachToRecyclerView(binding.dreamEntryRecyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.dreamLiveData.observe(
            viewLifecycleOwner,
            Observer { dreamWithEntries ->
                dreamWithEntries?.let {
                    this.dreamWithEntries = dreamWithEntries
                    photoFile = viewModel.getPhotoFile(dreamWithEntries.dream)
                    photoUri = FileProvider.getUriForFile(requireActivity(),
                        "edu.vt.cs.cs5254.dreamcatcher.fileprovider",
                        photoFile)
                    updateUI()
                }
            })
    }

    private fun getDreamReport(): String {
        val dreamStatus = if (dreamWithEntries.dream.isFulfilled) {
            getString(R.string.dream_fulfilled_report)
        } else if(dreamWithEntries.dream.isDeferred){
            getString(R.string.dream_deferred_report)
        }else{
            getString(R.string.dream_report)
        }
        val dreamTitle = ">> "+dreamWithEntries.dream.title+" <<"
        val dreamReflectionsLabel = getString(R.string.dream_reflections_label)
        val conceivedReport = R.string.dream_conceived_report
        val df = DateFormat.getMediumDateFormat(context)
        val dateString = df.format(dreamWithEntries.dream.date)
        var reflectionTextAdd = ""
        dreamWithEntries.dreamEntries.forEach { dreamEntry -> reflectionTextAdd += "-"+dreamEntry.text }
        return getString(conceivedReport, dreamTitle, dateString, dreamReflectionsLabel, reflectionTextAdd, dreamStatus)
    }

    override fun onStart() {
        super.onStart()

        binding.dreamTitleText.doOnTextChanged { text, start, before, count ->
            dreamWithEntries.dream.title = text.toString()
        }

        binding.dreamFulfilledCheckbox.apply {
            setOnCheckedChangeListener { _, isChecked ->
                dreamWithEntries.dream.isFulfilled = isChecked
            }
        }

        binding.dreamDeferredCheckbox.apply{
            setOnCheckedChangeListener { _, isChecked ->
                dreamWithEntries.dream.isDeferred = isChecked
            }
        }

        binding.addReflectionButton.setOnClickListener {
            AddReflectionDialog().show(parentFragmentManager, REQUEST_KEY_ADD_REFLECTION)
        }

        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ADD_REFLECTION,
            viewLifecycleOwner)
        { _, bundle ->
            reflectionText = bundle.getSerializable(BUNDLE_KEY_REFLECTION_TEXT) as String
            val newDreamEntry = DreamEntry(dreamId = dreamWithEntries.dream.id, kind=DreamEntryKind.REFLECTION, text = reflectionText)
            dreamWithEntries.dreamEntries+= newDreamEntry
            updateUI()
        }
        onFulfilledClick()
        onDeferredClick()
    }

    private fun onFulfilledClick() {
        binding.dreamFulfilledCheckbox.apply {
            setOnClickListener {
                if(binding.dreamFulfilledCheckbox.isChecked){
                    val newDreamEntry = DreamEntry(dreamId = dreamWithEntries.dream.id, kind=DreamEntryKind.FULFILLED)
                    dreamWithEntries.dreamEntries+= newDreamEntry
                    dreamWithEntries.dream.isFulfilled = true
                }
                if(!binding.dreamFulfilledCheckbox.isChecked){
                    binding.dreamDeferredCheckbox.isEnabled = true
                    dreamWithEntries.dreamEntries = dreamWithEntries.dreamEntries.filter{dreamEntry -> dreamEntry.kind != DreamEntryKind.FULFILLED}
                    dreamWithEntries.dream.isFulfilled = false

                }
                updateUI()
            }
        }
    }

    private fun onDeferredClick(){
        binding.dreamDeferredCheckbox.apply{
            setOnClickListener {
                if(binding.dreamDeferredCheckbox.isChecked){
                    dreamWithEntries.dreamEntries+= DreamEntry(dreamId = dreamWithEntries.dream.id, kind=DreamEntryKind.DEFERRED)
                    dreamWithEntries.dream.isDeferred = true

                }
                if(!binding.dreamDeferredCheckbox.isChecked){
                    binding.dreamFulfilledCheckbox.isEnabled = true
                    dreamWithEntries.dreamEntries = dreamWithEntries.dreamEntries.filter{dreamEntry -> dreamEntry.kind != DreamEntryKind.DEFERRED}
                    dreamWithEntries.dream.isDeferred = false
                }
                updateUI()
            }
        }
    }


    private fun refreshCheckboxes(){
        when{
              binding.dreamFulfilledCheckbox.isChecked -> {
                binding.dreamFulfilledCheckbox.isEnabled = true
                binding.dreamDeferredCheckbox.isChecked = false
                binding.dreamDeferredCheckbox.isEnabled = false
                  binding.addReflectionButton.isEnabled = false
            }
            binding.dreamDeferredCheckbox.isChecked -> {
                binding.dreamFulfilledCheckbox.isChecked = false
                binding.dreamDeferredCheckbox.isChecked = true
                binding.dreamFulfilledCheckbox.isEnabled = false
                binding.addReflectionButton.isEnabled = true
            }
            else -> {
                binding.dreamFulfilledCheckbox.isEnabled = true
                binding.dreamDeferredCheckbox.isEnabled = true
                binding.dreamFulfilledCheckbox.isChecked = false
                binding.dreamDeferredCheckbox.isChecked = false
                binding.addReflectionButton.isEnabled = true
            }
        }
    }

    private fun refreshEntryButton(button: Button, dreamEntry: DreamEntry){
        string = myString.toString() +" : "+ dreamEntry.text

        when (dreamEntry.kind) {
            DreamEntryKind.CONCEIVED -> {
                button.visibility = View.VISIBLE
                button.text = dreamEntry.kind.name
                setButtonColor(button, CONCEIVED_BUTTON_COLOR)
            }
            DreamEntryKind.REFLECTION -> {
                button.visibility = View.VISIBLE
                button.text = string
                setButtonColor(button, REFLECTION_BUTTON_COLOR)
            }
            DreamEntryKind.FULFILLED -> {
                button.visibility = View.VISIBLE
                button.text = dreamEntry.kind.name
                setButtonColor(button, FULFILLED_BUTTON_COLOR)
            }
            DreamEntryKind.DEFERRED -> {
                button.visibility = View.VISIBLE
                button.text = dreamEntry.kind.name
                setButtonColor(button, DEFERRED_BUTTON_COLOR)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveDreamEntry(dreamWithEntries)
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveDreamEntry(dreamWithEntries)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setButtonColor(button: Button, colorString: String) {
        button.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(colorString))
        button.setTextColor(Color.WHITE)
        button.alpha = 1f
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_dream_detail, menu)
        val cameraAvailable = PictureUtils.isCameraAvailable(requireActivity())
        val menuItem = menu.findItem(R.id.take_dream_photo)
        menuItem.apply {
            Log.d(TAG, "Camera available: $cameraAvailable")
            isEnabled = cameraAvailable
            isVisible = cameraAvailable
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share_dream -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getDreamReport())
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.dream_report_subject))
                }.also { intent ->
                    val chooserIntent =
                        Intent.createChooser(intent, getString(R.string.send_report))
                    startActivity(chooserIntent)
                }
                true
            }
            R.id.take_dream_photo -> {
                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                }
                requireActivity().packageManager
                    .queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    .forEach { cameraActivity ->
                        requireActivity().grantUriPermission(
                            cameraActivity.activityInfo.packageName,
                            photoUri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                photoLauncher.launch(photoUri)
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun updateUI(){
        binding.dreamFulfilledCheckbox.isChecked = dreamWithEntries.dream.isFulfilled
        binding.dreamDeferredCheckbox.isChecked = dreamWithEntries.dream.isDeferred
        //set text of the title
        binding.dreamTitleText.setText(dreamWithEntries.dream.title)
        //refresh the checkboxes
        refreshCheckboxes()
        updatePhotoView()
        adapter = DreamEntryAdapter()
        binding.dreamEntryRecyclerView.adapter = adapter
    }

    private fun updatePhotoView() {
        if (photoFile.exists()) {
            val bitmap = PictureUtils.getScaledBitmap(photoFile.path, 120, 120)
            binding.dreamPhoto.setImageBitmap(bitmap)
        } else {
            binding.dreamPhoto.setImageDrawable(null)
        }
    }

    private inner class SwipeToDeleteCallback(val adapter: DreamEntryAdapter?) :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val dreamEntryHolder = viewHolder as DreamDetailFragment.DreamEntryHolder
            val position = dreamEntryHolder.absoluteAdapterPosition
            if(dreamEntryHolder.dreamEntry.kind == DreamEntryKind.REFLECTION){
                dreamWithEntries.dreamEntries -= dreamWithEntries.dreamEntries[position]
            }
            updateUI()
        }
    }

    // DreamHolder && DreamAdapter
    inner class DreamEntryHolder(val detailBinding: ListItemDreamEntryBinding)
        : RecyclerView.ViewHolder(detailBinding.root), View.OnClickListener {
        private lateinit var dream: Dream
        lateinit var dreamEntry: DreamEntry
        init {
            itemView.setOnClickListener(this)
        }
        fun bind(dEntry: DreamEntry) {
            dreamEntry = dEntry
            refreshEntryButton(detailBinding.dreamEntryButton, dEntry)
        }

        override fun onClick(v: View) {
            callbacks?.onDreamSelected(dream.id)
        }
    }

    private inner class DreamEntryAdapter()
        : RecyclerView.Adapter<DreamEntryHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DreamEntryHolder {
            val itemBinding = ListItemDreamEntryBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)
            return DreamEntryHolder(itemBinding)
        }
        override fun getItemCount() = dreamWithEntries.dreamEntries.size
        override fun onBindViewHolder(holder: DreamEntryHolder, position: Int) {
            val dreamEntry = dreamWithEntries.dreamEntries[position]
            holder.bind(dreamEntry)
        }
    }


    companion object {
        fun newInstance(dreamId: UUID): DreamDetailFragment {
            val args = Bundle().apply {
                putSerializable(ARG_DREAM_ID, dreamId)
            }
            return DreamDetailFragment().apply {
                arguments = args
            }
        }
    }
}
















