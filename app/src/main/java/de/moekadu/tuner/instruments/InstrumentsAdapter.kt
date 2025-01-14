package de.moekadu.tuner.instruments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.moekadu.tuner.R
import de.moekadu.tuner.temperaments.NoteNames

class InstrumentDiffCallback : DiffUtil.ItemCallback<Instrument>() {
    override fun areItemsTheSame(oldItem: Instrument, newItem: Instrument): Boolean {
        return oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: Instrument, newItem: Instrument): Boolean {
        return oldItem == newItem
    }
}

fun RecyclerView.forEachViewHolder(op: (RecyclerView.ViewHolder) -> Unit) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        getChildViewHolder(child)?.let { viewHolder -> op(viewHolder) }
    }
}

class InstrumentsAdapter(val mode: Mode) : ListAdapter<Instrument, InstrumentsAdapter.ViewHolder>(
    InstrumentDiffCallback()
) {

    enum class Mode {Copy, EditCopy}

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        var titleView: TextView? = null
        var icon: ImageView? = null
        var editIcon: ImageView? = null
        var copyIcon: ImageView? = null
        var closeExpansionIcon: ImageView? = null
        var stringText: TextView? = null

        var selectedView: View? = null
        var instrument: Instrument? = null

        var isActivated = false
            set(value) {
                field = value
                if (value) {
                    selectedView?.visibility = View.VISIBLE
                }
                else {
                    selectedView?.visibility = View.INVISIBLE
                }
            }
    }

    interface OnInstrumentClickedListener {
        fun onInstrumentClicked(instrument: Instrument, stableId: Long)
        fun onEditIconClicked(instrument: Instrument, stableId: Long)
        fun onCopyIconClicked(instrument: Instrument, stableId: Long)
    }

    private var noteNames: NoteNames? = null
    private var preferFlat = false

    private var activatedStableId = Instrument.NO_STABLE_ID
    var onInstrumentClickedListener: OnInstrumentClickedListener? = null

    init {
        setHasStableIds(true)
    }

    fun setNoteNames(noteNames: NoteNames?, preferFlat: Boolean, recyclerView: RecyclerView?) {
        this.noteNames = noteNames
        this.preferFlat = preferFlat
        if (recyclerView == null || noteNames == null)
            return

        recyclerView.forEachViewHolder { holder ->
            if (holder is ViewHolder) {
                val position = holder.bindingAdapterPosition
                if (position >= 0) {
                    val instrument = getItem(position)
                    holder.stringText?.text = instrument.getStringsString(holder.view.context, noteNames, preferFlat = preferFlat)
                }
            }
        }
    }
//    override fun submitList(list: List<Instrument>?) {
//        Log.v("Tuner", "InstrumentsAdapter.submitList: size=${list?.size}, list=$list")
//        super.submitList(list)
//    }

    override fun getItemId(position: Int): Long {
//        Log.v("Tuner", "InstrumentAdapter.getItemId: position=$position, itemId=${getItem(position).stableId}")
        return getItem(position).stableId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.instrument_entry, parent, false)
        return ViewHolder(view).apply {
            titleView = view.findViewById(R.id.instrument_title)
            stringText = view.findViewById(R.id.string_list)
            icon = view.findViewById(R.id.instrument_icon)
            editIcon = view.findViewById(R.id.edit_instrument)
            editIcon?.setOnClickListener {
                onInstrumentClickedListener?.onEditIconClicked(getItem(bindingAdapterPosition), itemId)
            }
            editIcon?.setOnLongClickListener {
                closeExpansionIcon?.visibility = View.VISIBLE
                copyIcon?.visibility = View.VISIBLE
                editIcon?.setImageResource(R.drawable.ic_edit)
                true
            }
            copyIcon = view.findViewById(R.id.copy_instrument)
            copyIcon?.setOnClickListener {
                if (mode == Mode.EditCopy) {
                    closeExpansionIcon?.visibility = View.GONE
                    copyIcon?.visibility = View.GONE
                    editIcon?.setImageResource(R.drawable.ic_edit_expand)
                }
                onInstrumentClickedListener?.onCopyIconClicked(getItem(bindingAdapterPosition), itemId)
            }
            closeExpansionIcon = view.findViewById(R.id.close_expansion)
            closeExpansionIcon?.setOnClickListener {
                closeExpansionIcon?.visibility = View.GONE
                copyIcon?.visibility = View.GONE
                editIcon?.setImageResource(R.drawable.ic_edit_expand)
            }

            when (mode) {
                Mode.Copy -> {
                    closeExpansionIcon?.visibility = View.GONE
                    editIcon?.visibility = View.GONE
                    copyIcon?.visibility = View.VISIBLE
                }
                Mode.EditCopy -> {
                    closeExpansionIcon?.visibility = View.GONE
                    editIcon?.visibility = View.VISIBLE
                    editIcon?.setImageResource(R.drawable.ic_edit_expand)
                    copyIcon?.visibility = View.GONE
                }
            }

            selectedView = view.findViewById(R.id.instrument_active)
            view.setOnClickListener {
                activatedStableId = itemId
                onInstrumentClickedListener?.onInstrumentClicked(getItem(bindingAdapterPosition), itemId)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val instrument = getItem(position)
        holder.titleView?.text = instrument.getNameString(holder.view.context)
        noteNames?.let {
            holder.stringText?.text = instrument.getStringsString(
                holder.view.context,
                it,
                preferFlat = preferFlat
            )
        }
        holder.icon?.setImageResource(instrument.iconResource)
        holder.isActivated = (instrument.stableId == activatedStableId)
        holder.instrument = instrument

        // Extremely hacky way to disable copying the predefined chromatic instrument
        if (instrument.isChromatic && instrument.stableId < 0)
            holder.copyIcon?.visibility = View.GONE
        else if (instrument.stableId < 0)
            holder.copyIcon?.visibility = View.VISIBLE
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        holder.isActivated = (holder.itemId == activatedStableId)
        val position = holder.bindingAdapterPosition
        val instrument = getItem(position)
        holder.titleView?.text = instrument.getNameString(holder.view.context)
        noteNames?.let {
            holder.stringText?.text = instrument.getStringsString(
                holder.view.context,
                it,
                preferFlat = preferFlat
            )
        }
        holder.icon?.setImageResource(instrument.iconResource)
        holder.isActivated = (instrument.stableId == activatedStableId)
        holder.instrument = instrument
        super.onViewAttachedToWindow(holder)
    }

    fun setActiveStableId(stableId: Long, recyclerView: RecyclerView?) {
        activatedStableId = stableId
//        Log.v("Tuner", "InstrumentsAdapter.setActiveStableId: stableId=$stableId")
        if (recyclerView != null) {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val viewHolder = recyclerView.getChildViewHolder(child)
                if (viewHolder is ViewHolder) {
                    viewHolder.isActivated = (stableId == viewHolder.itemId)
                }
            }
        }
    }
}