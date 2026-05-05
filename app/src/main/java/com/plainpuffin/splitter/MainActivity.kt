package com.plainpuffin.splitter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREFS_NAME = "splitter_preferences"
private const val KEY_GROUP_NAME = "group_name"
private const val KEY_PEOPLE = "people"
private const val KEY_EXPENSES = "expenses"
private const val KEY_SETTLEMENT_ENTRIES = "settlement_entries"
private const val KEY_PROJECTS = "projects"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplitterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SplitterPalette.Background
                ) {
                    SplitterApp(getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
                }
            }
        }
    }
}

enum class SplitMode { EQUAL, CUSTOM }

data class Person(
    val id: String,
    val name: String
)

data class Expense(
    val id: String,
    val title: String,
    val amountCents: Long,
    val paidByPersonId: String,
    val splitMode: SplitMode,
    val participantIds: List<String>,
    val customSharesCents: Map<String, Long>,
    val note: String = ""
)

data class Settlement(
    val fromPersonId: String,
    val toPersonId: String,
    val amountCents: Long
)

data class SettlementEntry(
    val id: String,
    val fromPersonId: String,
    val toPersonId: String,
    val amountCents: Long,
    val note: String = ""
)

data class SplitEvent(
    val id: String,
    val groupName: String,
    val people: List<Person>,
    val expenses: List<Expense>,
    val settlementEntries: List<SettlementEntry>
)

private object SplitterPalette {
    val Background = Color(0xFF0B1020)
    val Panel = Color(0xFF121A2B)
    val PanelAlt = Color(0xFF1A2438)
    val Accent = Color(0xFF7CE3B2)
    val AccentMuted = Color(0xFF325A53)
    val Highlight = Color(0xFF85B7FF)
    val Text = Color(0xFFF6F8FC)
    val Subtle = Color(0xFFB3C0DA)
    val Border = Color(0xFF30415F)
    val InputBorder = Color(0xFF4B5E82)
    val Danger = Color(0xFFE07A7A)
}

@Composable
private fun SplitterTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun SplitterApp(preferences: SharedPreferences) {
    var events by remember { mutableStateOf(loadEvents(preferences)) }
    var currentEventId by remember { mutableStateOf<String?>(null) }

    fun persistEvents(updatedEvents: List<SplitEvent>) {
        events = updatedEvents
        preferences.edit()
            .putString(KEY_PROJECTS, encodeEvents(updatedEvents))
            .apply()
    }

    fun openEvent(eventId: String) {
        currentEventId = eventId
    }

    fun createEvent() {
        val event = SplitEvent(
            id = UUID.randomUUID().toString(),
            groupName = nextEventName(events),
            people = defaultPeople(),
            expenses = emptyList(),
            settlementEntries = emptyList()
        )
        persistEvents(events + event)
        currentEventId = event.id
    }

    fun updateEvent(updatedEvent: SplitEvent) {
        persistEvents(events.map { event ->
            if (event.id == updatedEvent.id) updatedEvent else event
        })
    }

    fun deleteEvent(eventId: String) {
        persistEvents(events.filterNot { it.id == eventId })
        if (currentEventId == eventId) {
            currentEventId = null
        }
    }

    val currentEvent = events.firstOrNull { it.id == currentEventId }

    if (currentEvent == null) {
        MainMenuScreen(
            events = events,
            onCreateEvent = ::createEvent,
            onOpenEvent = ::openEvent,
            onDeleteEvent = ::deleteEvent
        )
    } else {
        SplitEventScreen(
            event = currentEvent,
            onEventChange = ::updateEvent,
            onBackToMenu = { currentEventId = null }
        )
    }
}

@Composable
private fun MainMenuScreen(
    events: List<SplitEvent>,
    onCreateEvent: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onDeleteEvent: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplitterPalette.Background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SplitterMarkIcon(modifier = Modifier.size(112.dp))
            Text(text = "Splitter", style = titleStyle(), color = SplitterPalette.Text)
            Box(modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(text = "New event", onClick = onCreateEvent)
            }

            if (events.isEmpty()) {
                Text(
                    text = "No saved events yet.",
                    style = metaStyle(),
                    color = SplitterPalette.Subtle,
                    textAlign = TextAlign.Center
                )
            } else {
                events.sortedBy { it.groupName.lowercase(Locale.getDefault()) }.forEach { event ->
                    EventMenuCard(
                        event = event,
                        onOpen = { onOpenEvent(event.id) },
                        onDelete = { onDeleteEvent(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventMenuCard(event: SplitEvent, onOpen: () -> Unit, onDelete: () -> Unit) {
    PixelPanel {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = event.groupName.ifBlank { "Untitled event" },
                style = bodyStyle().copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = SplitterPalette.Text
            )
            Text(
                text = "${event.people.size} people • ${event.expenses.size} expenses • ${formatMoney(event.expenses.sumOf { it.amountCents })}",
                style = metaStyle(),
                color = SplitterPalette.Subtle,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(text = "Open event", onClick = onOpen)
                }
                SmallButton(text = "Delete", accent = SplitterPalette.Danger, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun SplitEventScreen(
    event: SplitEvent,
    onEventChange: (SplitEvent) -> Unit,
    onBackToMenu: () -> Unit
) {
    BackHandler(onBack = onBackToMenu)

    val context = androidx.compose.ui.platform.LocalContext.current

    var groupName by remember(event.id) { mutableStateOf(event.groupName) }
    var people by remember(event.id) { mutableStateOf(event.people.ifEmpty { defaultPeople() }) }
    var expenses by remember(event.id) { mutableStateOf(event.expenses) }
    var settlementEntries by remember(event.id) { mutableStateOf(event.settlementEntries) }

    var expenseTitle by remember(event.id) { mutableStateOf("") }
    var amountInput by remember(event.id) { mutableStateOf("") }
    var noteInput by remember(event.id) { mutableStateOf("") }
    var splitMode by remember(event.id) { mutableStateOf(SplitMode.EQUAL) }
    var paidByPersonId by remember(event.id) { mutableStateOf(people.firstOrNull()?.id.orEmpty()) }
    var selectedParticipantIds by remember(event.id) { mutableStateOf(people.map { it.id }.toSet()) }
    var customShareInputs by remember(event.id) { mutableStateOf(people.associate { it.id to "" }) }
    var editingExpenseId by remember(event.id) { mutableStateOf<String?>(null) }
    var settleFromPersonId by remember(event.id) { mutableStateOf(people.firstOrNull()?.id.orEmpty()) }
    var settleToPersonId by remember(event.id) { mutableStateOf(people.getOrNull(1)?.id ?: people.firstOrNull()?.id.orEmpty()) }
    var settleAmountInput by remember(event.id) { mutableStateOf("") }
    var settleNoteInput by remember(event.id) { mutableStateOf("") }

    fun persistCore(
        updatedGroupName: String = groupName,
        updatedPeople: List<Person> = people,
        updatedExpenses: List<Expense> = expenses,
        updatedSettlementEntries: List<SettlementEntry> = settlementEntries
    ) {
        groupName = updatedGroupName
        people = updatedPeople
        expenses = updatedExpenses
        settlementEntries = updatedSettlementEntries
        onEventChange(
            SplitEvent(
                id = event.id,
                groupName = updatedGroupName,
                people = updatedPeople,
                expenses = updatedExpenses,
                settlementEntries = updatedSettlementEntries
            )
        )
    }

    fun syncDraftState(updatedPeople: List<Person>) {
        val ids = updatedPeople.map { it.id }
        if (paidByPersonId !in ids) {
            paidByPersonId = ids.firstOrNull().orEmpty()
        }
        selectedParticipantIds = selectedParticipantIds.filter { it in ids }.toMutableSet().ifEmpty { ids.toSet() }
        customShareInputs = ids.associateWith { customShareInputs[it] ?: "" }
        if (settleFromPersonId !in ids) {
            settleFromPersonId = ids.firstOrNull().orEmpty()
        }
        if (settleToPersonId !in ids || settleToPersonId == settleFromPersonId) {
            settleToPersonId = ids.firstOrNull { it != settleFromPersonId } ?: ids.firstOrNull().orEmpty()
        }
    }

    fun updatePeople(updatedPeople: List<Person>) {
        persistCore(updatedPeople = updatedPeople)
        syncDraftState(updatedPeople)
    }

    fun addPerson() {
        val nextNumber = people.size + 1
        val updatedPeople = people + Person(id = UUID.randomUUID().toString(), name = "Person $nextNumber")
        updatePeople(updatedPeople)
    }

    fun removePerson(personId: String) {
        if (people.size <= 2) {
            Toast.makeText(context, "Keep at least two people in the event.", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedPeople = people.filterNot { it.id == personId }
        val updatedExpenses = expenses.filterNot { it.paidByPersonId == personId || personId in it.participantIds }
        val updatedSettlementEntries = settlementEntries.filterNot { it.fromPersonId == personId || it.toPersonId == personId }
        persistCore(updatedPeople = updatedPeople, updatedExpenses = updatedExpenses, updatedSettlementEntries = updatedSettlementEntries)
        syncDraftState(updatedPeople)
    }

    fun updatePersonName(personId: String, name: String) {
        updatePeople(people.map { if (it.id == personId) it.copy(name = sanitizeNameInput(name)) else it })
    }

    fun toggleParticipant(personId: String, enabled: Boolean) {
        selectedParticipantIds = if (enabled) selectedParticipantIds + personId else selectedParticipantIds - personId
    }

    fun clearDraft() {
        editingExpenseId = null
        expenseTitle = ""
        amountInput = ""
        noteInput = ""
        splitMode = SplitMode.EQUAL
        selectedParticipantIds = people.map { it.id }.toSet()
        customShareInputs = people.associate { it.id to "" }
        if (paidByPersonId !in people.map { it.id }) {
            paidByPersonId = people.firstOrNull()?.id.orEmpty()
        }
    }

    fun startEditingExpense(expense: Expense) {
        editingExpenseId = expense.id
        expenseTitle = expense.title
        amountInput = formatEditableMoney(expense.amountCents)
        noteInput = expense.note
        splitMode = expense.splitMode
        paidByPersonId = expense.paidByPersonId
        selectedParticipantIds = expense.participantIds.toSet()
        customShareInputs = people.associate { person ->
            person.id to if (expense.splitMode == SplitMode.CUSTOM) {
                expense.customSharesCents[person.id]?.takeIf { it > 0L }?.let(::formatEditableMoney).orEmpty()
            } else {
                ""
            }
        }
    }

    fun submitExpense() {
        val amountCents = parseMoneyToCents(amountInput)
        if (amountCents <= 0L) {
            Toast.makeText(context, "Enter a valid amount.", Toast.LENGTH_SHORT).show()
            return
        }
        if (people.isEmpty() || paidByPersonId.isBlank()) {
            Toast.makeText(context, "Add people and choose who paid.", Toast.LENGTH_SHORT).show()
            return
        }
        val participants = people.filter { it.id in selectedParticipantIds }
        if (participants.isEmpty()) {
            Toast.makeText(context, "Choose at least one participant.", Toast.LENGTH_SHORT).show()
            return
        }

        val customShares = if (splitMode == SplitMode.CUSTOM) {
            val parsed = participants.associate { person -> person.id to parseMoneyToCents(customShareInputs[person.id].orEmpty()) }
            val sum = parsed.values.sum()
            if (sum != amountCents) {
                Toast.makeText(context, "Custom shares must add up exactly to the total amount.", Toast.LENGTH_SHORT).show()
                return
            }
            parsed
        } else {
            emptyMap()
        }

        val existingExpenseId = editingExpenseId
        val expense = Expense(
            id = existingExpenseId ?: UUID.randomUUID().toString(),
            title = expenseTitle.trim().ifBlank { "Expense" },
            amountCents = amountCents,
            paidByPersonId = paidByPersonId,
            splitMode = splitMode,
            participantIds = participants.map { it.id },
            customSharesCents = customShares,
            note = noteInput.trim()
        )

        val updatedExpenses = if (existingExpenseId == null) {
            expenses + expense
        } else if (expenses.any { it.id == existingExpenseId }) {
            expenses.map { if (it.id == existingExpenseId) expense else it }
        } else {
            expenses + expense
        }

        persistCore(updatedExpenses = updatedExpenses)
        clearDraft()
        Toast.makeText(context, if (existingExpenseId == null) "Expense added." else "Expense updated.", Toast.LENGTH_SHORT).show()
    }

    fun addSettlementEntry() {
        val amountCents = parseMoneyToCents(settleAmountInput)
        if (amountCents <= 0L) {
            Toast.makeText(context, "Enter a valid settle-up amount.", Toast.LENGTH_SHORT).show()
            return
        }
        if (settleFromPersonId.isBlank() || settleToPersonId.isBlank() || settleFromPersonId == settleToPersonId) {
            Toast.makeText(context, "Choose two different people for the settle-up.", Toast.LENGTH_SHORT).show()
            return
        }
        val entry = SettlementEntry(
            id = UUID.randomUUID().toString(),
            fromPersonId = settleFromPersonId,
            toPersonId = settleToPersonId,
            amountCents = amountCents,
            note = settleNoteInput.trim()
        )
        persistCore(updatedSettlementEntries = settlementEntries + entry)
        settleAmountInput = ""
        settleNoteInput = ""
        Toast.makeText(context, "Settle-up added.", Toast.LENGTH_SHORT).show()
    }

    val balances = remember(people, expenses, settlementEntries) { computeBalances(people, expenses, settlementEntries) }
    val settlements = remember(people, balances) { computeSettlements(balances) }
    val totalDraftAmountCents = parseMoneyToCents(amountInput)
    val customShareDeltaCents = if (splitMode == SplitMode.CUSTOM) {
        totalDraftAmountCents - selectedParticipantIds.sumOf { personId ->
            parseMoneyToCents(customShareInputs[personId].orEmpty())
        }
    } else {
        0L
    }
    val customShareStatus = when {
        splitMode != SplitMode.CUSTOM -> null
        totalDraftAmountCents <= 0L -> "Enter the expense total first to guide the custom split."
        customShareDeltaCents == 0L -> "Custom split matches the expense total."
        customShareDeltaCents > 0L -> "Remaining ${formatMoney(customShareDeltaCents)} to assign."
        else -> "Over by ${formatMoney(-customShareDeltaCents)}."
    }
    val customShareStatusColor = when {
        splitMode != SplitMode.CUSTOM -> SplitterPalette.Subtle
        totalDraftAmountCents <= 0L -> SplitterPalette.Subtle
        customShareDeltaCents == 0L -> SplitterPalette.Accent
        customShareDeltaCents > 0L -> SplitterPalette.Highlight
        else -> SplitterPalette.Danger
    }
    val settlementSummary = remember(groupName, people, settlements) { buildSettlementSummary(groupName, people, settlements) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SplitterPalette.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProjectNamePanel(
            projectName = groupName,
            onProjectNameChange = {
                val sanitized = sanitizeTitleInput(it).ifBlank { "Untitled event" }
                persistCore(updatedGroupName = sanitized)
            },
            onBackToMenu = onBackToMenu
        )

        SummaryPanel(
            peopleCount = people.size,
            expenseCount = expenses.size,
            totalCents = expenses.sumOf { it.amountCents }
        )

        PeoplePanel(
            people = people,
            onNameChange = ::updatePersonName,
            onAddPerson = ::addPerson,
            onRemovePerson = ::removePerson
        )

        AddExpensePanel(
            people = people,
            expenseTitle = expenseTitle,
            amountInput = amountInput,
            noteInput = noteInput,
            paidByPersonId = paidByPersonId,
            splitMode = splitMode,
            isEditing = editingExpenseId != null,
            selectedParticipantIds = selectedParticipantIds,
            customShareInputs = customShareInputs,
            onTitleChange = { expenseTitle = sanitizeTitleInput(it) },
            onAmountChange = { amountInput = sanitizeMoneyInput(it) },
            onNoteChange = { noteInput = sanitizeNoteInput(it) },
            onPaidByChange = { paidByPersonId = it },
            onSplitModeChange = { splitMode = it },
            onToggleParticipant = ::toggleParticipant,
            onSelectAllParticipants = { selectedParticipantIds = people.map { it.id }.toSet() },
            onClearParticipants = { selectedParticipantIds = emptySet() },
            onCustomShareChange = { personId, value -> customShareInputs = customShareInputs + (personId to sanitizeMoneyInput(value)) },
            customShareStatus = customShareStatus,
            customShareStatusColor = customShareStatusColor,
            onSubmitExpense = ::submitExpense,
            onCancelEditing = ::clearDraft
        )

        SettleUpPanel(
            people = people,
            fromPersonId = settleFromPersonId,
            toPersonId = settleToPersonId,
            amountInput = settleAmountInput,
            noteInput = settleNoteInput,
            onFromPersonChange = {
                settleFromPersonId = it
                if (settleToPersonId == it) {
                    settleToPersonId = people.firstOrNull { person -> person.id != it }?.id.orEmpty()
                }
            },
            onToPersonChange = { settleToPersonId = it },
            onAmountChange = { settleAmountInput = sanitizeMoneyInput(it) },
            onNoteChange = { settleNoteInput = sanitizeNoteInput(it) },
            onAddSettleUp = ::addSettlementEntry
        )

        BalancesPanel(people = people, balances = balances, settlements = settlements)

        RecordedSettleUpsPanel(
            people = people,
            settlementEntries = settlementEntries,
            onDeleteSettlementEntry = { entryId ->
                persistCore(updatedSettlementEntries = settlementEntries.filterNot { it.id == entryId })
                Toast.makeText(context, "Settle-up removed.", Toast.LENGTH_SHORT).show()
            }
        )

        ExpensesPanel(
            people = people,
            expenses = expenses,
            onEditExpense = ::startEditingExpense,
            onDeleteExpense = { expenseId ->
                persistCore(updatedExpenses = expenses.filterNot { it.id == expenseId })
                if (editingExpenseId == expenseId) {
                    clearDraft()
                }
                Toast.makeText(context, "Expense removed.", Toast.LENGTH_SHORT).show()
            }
        )

        ActionPanel(
            hasSettlements = settlements.isNotEmpty(),
            isEditing = editingExpenseId != null,
            onCopySettlementSummary = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Splitter settle-up", settlementSummary))
                Toast.makeText(context, "Settle-up summary copied.", Toast.LENGTH_SHORT).show()
            },
            onCancelEditing = ::clearDraft,
            onSaveAndExit = onBackToMenu
        )
    }
}

@Composable
private fun ProjectNamePanel(
    projectName: String,
    onProjectNameChange: (String) -> Unit,
    onBackToMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        SquareNavPanel(onClick = onBackToMenu)
        Box(modifier = Modifier.weight(1f)) {
            PixelPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "EVENT", style = labelStyle(), color = SplitterPalette.Highlight)
                    StyledTextField(
                        value = projectName,
                        placeholder = "Event name",
                        onValueChange = onProjectNameChange
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(peopleCount: Int, expenseCount: Int, totalCents: Long) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "SUMMARY", style = labelStyle(), color = SplitterPalette.Highlight)
            SummaryLine(label = "People", value = peopleCount.toString())
            SummaryLine(label = "Expenses", value = expenseCount.toString())
            SummaryLine(label = "Tracked total", value = formatMoney(totalCents), emphasized = true)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = bodyStyle(), color = SplitterPalette.Subtle)
        Text(text = value, style = if (emphasized) valueStyle() else bodyStyle(), color = SplitterPalette.Text)
    }
}

@Composable
private fun PeoplePanel(
    people: List<Person>,
    onNameChange: (String, String) -> Unit,
    onAddPerson: () -> Unit,
    onRemovePerson: (String) -> Unit
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "PEOPLE", style = labelStyle(), color = SplitterPalette.Highlight)
            people.forEach { person ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StyledTextField(
                            value = person.name,
                            placeholder = "Name",
                            onValueChange = { onNameChange(person.id, it) }
                        )
                    }
                    SmallButton(text = "Remove", accent = SplitterPalette.Danger, onClick = { onRemovePerson(person.id) })
                }
            }
            PrimaryButton(text = "+ Add person", onClick = onAddPerson, accent = SplitterPalette.AccentMuted)
        }
    }
}

@Composable
private fun AddExpensePanel(
    people: List<Person>,
    expenseTitle: String,
    amountInput: String,
    noteInput: String,
    paidByPersonId: String,
    splitMode: SplitMode,
    isEditing: Boolean,
    selectedParticipantIds: Set<String>,
    customShareInputs: Map<String, String>,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPaidByChange: (String) -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onToggleParticipant: (String, Boolean) -> Unit,
    onSelectAllParticipants: () -> Unit,
    onClearParticipants: () -> Unit,
    onCustomShareChange: (String, String) -> Unit,
    customShareStatus: String?,
    customShareStatusColor: Color,
    onSubmitExpense: () -> Unit,
    onCancelEditing: () -> Unit
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = if (isEditing) "EDIT EXPENSE" else "ADD EXPENSE", style = labelStyle(), color = SplitterPalette.Highlight)
            StyledTextField(value = expenseTitle, placeholder = "Dinner, rent, groceries...", onValueChange = onTitleChange)
            StyledTextField(value = amountInput, placeholder = "0", suffix = "kr", onValueChange = onAmountChange)
            StyledTextField(value = noteInput, placeholder = "Optional note", onValueChange = onNoteChange)

            Text(text = "WHO PAID", style = labelStyle(), color = SplitterPalette.Highlight)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { person ->
                    ToggleRowButton(
                        text = person.name.ifBlank { "Unnamed" },
                        selected = person.id == paidByPersonId,
                        onClick = { onPaidByChange(person.id) }
                    )
                }
            }

            Text(text = "SPLIT MODE", style = labelStyle(), color = SplitterPalette.Highlight)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(text = "Equal", selected = splitMode == SplitMode.EQUAL, onClick = { onSplitModeChange(SplitMode.EQUAL) })
                ToggleChip(text = "Custom", selected = splitMode == SplitMode.CUSTOM, onClick = { onSplitModeChange(SplitMode.CUSTOM) })
            }

            Text(text = "PARTICIPANTS", style = labelStyle(), color = SplitterPalette.Highlight)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(text = "All", accent = SplitterPalette.PanelAlt, onClick = onSelectAllParticipants)
                SmallButton(text = "Clear", accent = SplitterPalette.PanelAlt, onClick = onClearParticipants)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitterPalette.Border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = person.id in selectedParticipantIds,
                            onCheckedChange = { onToggleParticipant(person.id, it) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = person.name.ifBlank { "Unnamed" }, style = bodyStyle(), color = SplitterPalette.Text)
                            if (splitMode == SplitMode.EQUAL) {
                                Text(text = "Included in equal split", style = metaStyle(), color = SplitterPalette.Subtle)
                            }
                        }
                        if (splitMode == SplitMode.CUSTOM && person.id in selectedParticipantIds) {
                            Box(modifier = Modifier.width(120.dp)) {
                                StyledTextField(
                                    value = customShareInputs[person.id].orEmpty(),
                                    placeholder = "0",
                                    suffix = "kr",
                                    onValueChange = { onCustomShareChange(person.id, it) }
                                )
                            }
                        }
                    }
                }
            }

            if (splitMode == SplitMode.CUSTOM) {
                Text(
                    text = customShareStatus ?: "Custom mode requires participant shares to add up exactly to the expense total.",
                    style = metaStyle(),
                    color = customShareStatusColor
                )
            }

            if (isEditing) {
                PrimaryButton(text = "Cancel editing", onClick = onCancelEditing, accent = SplitterPalette.PanelAlt)
            }
            PrimaryButton(text = if (isEditing) "Save changes" else "Add expense", onClick = onSubmitExpense)
        }
    }
}

@Composable
@Composable
private fun SquareNavPanel(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SplitterPalette.Panel),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(88.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, SplitterPalette.Border, RoundedCornerShape(10.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = SplitterPalette.Panel,
                contentColor = SplitterPalette.Text
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "←", style = titleStyle(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BalancesPanel(
    people: List<Person>,
    balances: Map<String, Long>,
    settlements: List<Settlement>
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "BALANCES", style = labelStyle(), color = SplitterPalette.Highlight)
            people.forEach { person ->
                val balance = balances[person.id] ?: 0L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = person.name.ifBlank { "Unnamed" }, style = bodyStyle(), color = SplitterPalette.Text)
                    Text(
                        text = when {
                            balance > 0L -> "gets back ${formatMoney(balance)}"
                            balance < 0L -> "owes ${formatMoney(abs(balance))}"
                            else -> "settled"
                        },
                        style = bodyStyle(),
                        color = when {
                            balance > 0L -> SplitterPalette.Accent
                            balance < 0L -> SplitterPalette.Highlight
                            else -> SplitterPalette.Subtle
                        }
                    )
                }
            }

            HorizontalDivider(color = SplitterPalette.Border)
            Text(text = "SETTLE UP", style = labelStyle(), color = SplitterPalette.Highlight)
            if (settlements.isEmpty()) {
                Text(text = "Everything is settled.", style = bodyStyle(), color = SplitterPalette.Subtle)
            } else {
                settlements.forEach { settlement ->
                    val fromName = people.firstOrNull { it.id == settlement.fromPersonId }?.name.orEmpty().ifBlank { "Someone" }
                    val toName = people.firstOrNull { it.id == settlement.toPersonId }?.name.orEmpty().ifBlank { "Someone" }
                    Text(
                        text = "$fromName pays $toName ${formatMoney(settlement.amountCents)}",
                        style = bodyStyle(),
                        color = SplitterPalette.Text
                    )
                }
            }
        }
    }
}

@Composable
private fun SettleUpPanel(
    people: List<Person>,
    fromPersonId: String,
    toPersonId: String,
    amountInput: String,
    noteInput: String,
    onFromPersonChange: (String) -> Unit,
    onToPersonChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAddSettleUp: () -> Unit
) {
    val receiveCandidates = people.filter { it.id != fromPersonId }

    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "ADD SETTLE-UP", style = labelStyle(), color = SplitterPalette.Highlight)
            Text(
                text = "Record a real payment between two people, even if it is not the exact suggested amount.",
                style = metaStyle(),
                color = SplitterPalette.Subtle
            )

            Text(text = "WHO PAID", style = labelStyle(), color = SplitterPalette.Highlight)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { person ->
                    ToggleRowButton(
                        text = person.name.ifBlank { "Unnamed" },
                        selected = person.id == fromPersonId,
                        onClick = { onFromPersonChange(person.id) }
                    )
                }
            }

            Text(text = "WHO RECEIVED", style = labelStyle(), color = SplitterPalette.Highlight)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                receiveCandidates.forEach { person ->
                    ToggleRowButton(
                        text = person.name.ifBlank { "Unnamed" },
                        selected = person.id == toPersonId,
                        onClick = { onToPersonChange(person.id) }
                    )
                }
            }

            StyledTextField(value = amountInput, placeholder = "0", suffix = "kr", onValueChange = onAmountChange)
            StyledTextField(value = noteInput, placeholder = "Optional note", onValueChange = onNoteChange)
            PrimaryButton(text = "Add settle-up", onClick = onAddSettleUp, accent = SplitterPalette.Highlight)
        }
    }
}

@Composable
private fun RecordedSettleUpsPanel(
    people: List<Person>,
    settlementEntries: List<SettlementEntry>,
    onDeleteSettlementEntry: (String) -> Unit
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "RECORDED SETTLE-UPS", style = labelStyle(), color = SplitterPalette.Highlight)
            if (settlementEntries.isEmpty()) {
                Text(text = "No settle-ups recorded yet.", style = bodyStyle(), color = SplitterPalette.Subtle)
            } else {
                settlementEntries.asReversed().forEach { entry ->
                    val fromName = people.firstOrNull { it.id == entry.fromPersonId }?.name.orEmpty().ifBlank { "Someone" }
                    val toName = people.firstOrNull { it.id == entry.toPersonId }?.name.orEmpty().ifBlank { "Someone" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitterPalette.Border, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "$fromName paid $toName ${formatMoney(entry.amountCents)}", style = bodyStyle(), color = SplitterPalette.Text)
                        if (entry.note.isNotBlank()) {
                            Text(text = entry.note, style = metaStyle(), color = SplitterPalette.Subtle)
                        }
                        SmallButton(text = "Delete", accent = SplitterPalette.Danger, onClick = { onDeleteSettlementEntry(entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesPanel(
    people: List<Person>,
    expenses: List<Expense>,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "EXPENSES", style = labelStyle(), color = SplitterPalette.Highlight)
            if (expenses.isEmpty()) {
                Text(text = "No expenses yet.", style = bodyStyle(), color = SplitterPalette.Subtle)
            } else {
                expenses.asReversed().forEach { expense ->
                    val payerName = people.firstOrNull { it.id == expense.paidByPersonId }?.name.orEmpty().ifBlank { "Unknown" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitterPalette.Border, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = expense.title, style = bodyStyle(), color = SplitterPalette.Text)
                                Text(
                                    text = "Paid by $payerName • ${if (expense.splitMode == SplitMode.EQUAL) "Equal split" else "Custom split"}",
                                    style = metaStyle(),
                                    color = SplitterPalette.Subtle
                                )
                            }
                            Text(text = formatMoney(expense.amountCents), style = valueStyle(), color = SplitterPalette.Accent)
                        }
                        if (expense.note.isNotBlank()) {
                            Text(text = expense.note, style = metaStyle(), color = SplitterPalette.Subtle)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(text = "Edit", accent = SplitterPalette.Highlight, onClick = { onEditExpense(expense) })
                            SmallButton(text = "Delete", accent = SplitterPalette.Danger, onClick = { onDeleteExpense(expense.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(
    hasSettlements: Boolean,
    isEditing: Boolean,
    onCopySettlementSummary: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveAndExit: () -> Unit
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "CONTROLS", style = labelStyle(), color = SplitterPalette.Highlight)
            PrimaryButton(
                text = "Copy settle-up summary",
                onClick = onCopySettlementSummary,
                accent = SplitterPalette.Highlight,
                enabled = hasSettlements
            )
            if (isEditing) {
                PrimaryButton(
                    text = "Cancel editing",
                    onClick = onCancelEditing,
                    accent = SplitterPalette.PanelAlt
                )
            }
            PrimaryButton(
                text = "Save and exit",
                onClick = onSaveAndExit,
                accent = SplitterPalette.PanelAlt
            )
        }
    }
}

@Composable
private fun SplitterMarkIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(2.dp, SplitterPalette.Border, RoundedCornerShape(10.dp))
            .background(SplitterPalette.PanelAlt, RoundedCornerShape(10.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_splitter),
            contentDescription = "Splitter icon",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun StyledTextField(
    value: String,
    placeholder: String,
    suffix: String? = null,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = bodyStyle().copy(color = SplitterPalette.Text),
        placeholder = { Text(text = placeholder, style = bodyStyle(), color = SplitterPalette.Subtle) },
        trailingIcon = suffix?.let { { Text(text = it, style = metaStyle(), color = SplitterPalette.Subtle) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (suffix == null) KeyboardType.Text else KeyboardType.Decimal),
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SplitterPalette.Background,
            unfocusedContainerColor = SplitterPalette.Background,
            disabledContainerColor = SplitterPalette.Background,
            focusedIndicatorColor = SplitterPalette.InputBorder,
            unfocusedIndicatorColor = SplitterPalette.InputBorder,
            cursorColor = SplitterPalette.Accent,
            focusedTextColor = SplitterPalette.Text,
            unfocusedTextColor = SplitterPalette.Text,
            focusedPlaceholderColor = SplitterPalette.Subtle,
            unfocusedPlaceholderColor = SplitterPalette.Subtle
        )
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, accent: Color = SplitterPalette.Accent, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = buttonContentColor(accent),
            disabledContainerColor = SplitterPalette.PanelAlt,
            disabledContentColor = SplitterPalette.Subtle
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, buttonBorderColor(accent))
    ) {
        Text(text = text, style = bodyStyle(), modifier = Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallButton(text: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonContentColor(accent)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, buttonBorderColor(accent))
    ) {
        Text(text = text, style = metaStyle())
    }
}

@Composable
private fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) SplitterPalette.Highlight else SplitterPalette.PanelAlt,
            contentColor = if (selected) buttonContentColor(SplitterPalette.Highlight) else SplitterPalette.Text
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, if (selected) SplitterPalette.Highlight else SplitterPalette.InputBorder)
    ) {
        Text(text = text, style = metaStyle())
    }
}

@Composable
private fun ToggleRowButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) SplitterPalette.Accent else SplitterPalette.PanelAlt,
            contentColor = if (selected) buttonContentColor(SplitterPalette.Accent) else SplitterPalette.Text
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, if (selected) SplitterPalette.Accent else SplitterPalette.InputBorder)
    ) {
        Text(text = text, style = bodyStyle())
    }
}

private fun buttonContentColor(accent: Color): Color {
    return if (accent.luminance() > 0.42f) SplitterPalette.Background else SplitterPalette.Text
}

private fun buttonBorderColor(accent: Color): Color {
    return if (accent.luminance() > 0.42f) SplitterPalette.Background.copy(alpha = 0.55f) else SplitterPalette.Highlight
}

@Composable
private fun PixelPanel(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SplitterPalette.Panel),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, SplitterPalette.Border, RoundedCornerShape(10.dp))
                .background(SplitterPalette.Panel)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

private fun computeBalances(people: List<Person>, expenses: List<Expense>, settlementEntries: List<SettlementEntry>): Map<String, Long> {
    val balances = people.associate { it.id to 0L }.toMutableMap()
    expenses.forEach { expense ->
        if (expense.paidByPersonId !in balances) return@forEach
        balances[expense.paidByPersonId] = (balances[expense.paidByPersonId] ?: 0L) + expense.amountCents
        val shares = when (expense.splitMode) {
            SplitMode.EQUAL -> equalShares(expense.amountCents, expense.participantIds)
            SplitMode.CUSTOM -> expense.customSharesCents
        }
        shares.forEach { (personId, shareCents) ->
            if (personId in balances) {
                balances[personId] = (balances[personId] ?: 0L) - shareCents
            }
        }
    }
    settlementEntries.forEach { entry ->
        if (entry.fromPersonId in balances) {
            balances[entry.fromPersonId] = (balances[entry.fromPersonId] ?: 0L) + entry.amountCents
        }
        if (entry.toPersonId in balances) {
            balances[entry.toPersonId] = (balances[entry.toPersonId] ?: 0L) - entry.amountCents
        }
    }
    return balances
}

private fun equalShares(totalCents: Long, participantIds: List<String>): Map<String, Long> {
    if (participantIds.isEmpty()) return emptyMap()
    val sortedIds = participantIds.sorted()
    val base = totalCents / sortedIds.size
    val remainder = totalCents % sortedIds.size
    return sortedIds.mapIndexed { index, personId ->
        personId to (base + if (index < remainder) 1L else 0L)
    }.toMap()
}

private fun computeSettlements(balances: Map<String, Long>): List<Settlement> {
    val creditors = balances.filterValues { it > 0L }
        .map { it.key to it.value }
        .sortedByDescending { it.second }
        .toMutableList()
    val debtors = balances.filterValues { it < 0L }
        .map { it.key to -it.value }
        .sortedByDescending { it.second }
        .toMutableList()

    val settlements = mutableListOf<Settlement>()
    var creditorIndex = 0
    var debtorIndex = 0
    while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
        val (creditorId, credit) = creditors[creditorIndex]
        val (debtorId, debt) = debtors[debtorIndex]
        val amount = minOf(credit, debt)
        if (amount > 0L) {
            settlements += Settlement(fromPersonId = debtorId, toPersonId = creditorId, amountCents = amount)
        }
        creditors[creditorIndex] = creditorId to (credit - amount)
        debtors[debtorIndex] = debtorId to (debt - amount)
        if (creditors[creditorIndex].second == 0L) creditorIndex++
        if (debtors[debtorIndex].second == 0L) debtorIndex++
    }
    return settlements
}

private fun loadEvents(preferences: SharedPreferences): List<SplitEvent> {
    val storedEvents = parseEvents(preferences.getString(KEY_PROJECTS, null))
    if (storedEvents.isNotEmpty()) {
        return storedEvents
    }

    val legacyGroupName = preferences.getString(KEY_GROUP_NAME, "Apartment group") ?: "Apartment group"
    val legacyPeople = loadPeopleLegacy(preferences)
    val legacyExpenses = loadExpenses(preferences)
    val legacySettlementEntries = loadSettlementEntries(preferences)

    return listOf(
        SplitEvent(
            id = UUID.randomUUID().toString(),
            groupName = legacyGroupName,
            people = legacyPeople,
            expenses = legacyExpenses,
            settlementEntries = legacySettlementEntries
        )
    )
}

private fun parseEvents(raw: String?): List<SplitEvent> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SplitEvent(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        groupName = item.optString("groupName", "Untitled event"),
                        people = parsePeople(item.optJSONArray("people")?.toString()).ifEmpty { defaultPeople() },
                        expenses = parseExpenses(item.optJSONArray("expenses")?.toString()),
                        settlementEntries = parseSettlementEntries(item.optJSONArray("settlementEntries")?.toString())
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun encodeEvents(events: List<SplitEvent>): String {
    val array = JSONArray()
    events.forEach { event ->
        array.put(
            JSONObject()
                .put("id", event.id)
                .put("groupName", event.groupName)
                .put("people", JSONArray(encodePeople(event.people)))
                .put("expenses", JSONArray(encodeExpenses(event.expenses)))
                .put("settlementEntries", JSONArray(encodeSettlementEntries(event.settlementEntries)))
        )
    }
    return array.toString()
}

private fun loadPeopleLegacy(preferences: SharedPreferences): List<Person> {
    val stored = preferences.getString(KEY_PEOPLE, null)
    val parsed = parsePeople(stored)
    return if (parsed.isEmpty()) defaultPeople() else parsed
}

private fun loadExpenses(preferences: SharedPreferences): List<Expense> = parseExpenses(preferences.getString(KEY_EXPENSES, null))

private fun loadSettlementEntries(preferences: SharedPreferences): List<SettlementEntry> = parseSettlementEntries(preferences.getString(KEY_SETTLEMENT_ENTRIES, null))

private fun parsePeople(raw: String?): List<Person> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Person(id = item.getString("id"), name = item.optString("name", "")))
            }
        }
    }.getOrDefault(emptyList())
}

private fun encodePeople(people: List<Person>): String {
    val array = JSONArray()
    people.forEach { person ->
        array.put(JSONObject().put("id", person.id).put("name", person.name))
    }
    return array.toString()
}

private fun parseExpenses(raw: String?): List<Expense> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val participantsArray = item.optJSONArray("participantIds") ?: JSONArray()
                val customSharesObject = item.optJSONObject("customSharesCents") ?: JSONObject()
                val customShares = mutableMapOf<String, Long>()
                customSharesObject.keys().forEach { key ->
                    customShares[key] = customSharesObject.optLong(key, 0L)
                }
                add(
                    Expense(
                        id = item.getString("id"),
                        title = item.optString("title", "Expense"),
                        amountCents = item.optLong("amountCents", 0L),
                        paidByPersonId = item.optString("paidByPersonId", ""),
                        splitMode = SplitMode.valueOf(item.optString("splitMode", SplitMode.EQUAL.name)),
                        participantIds = buildList {
                            for (participantIndex in 0 until participantsArray.length()) {
                                add(participantsArray.getString(participantIndex))
                            }
                        },
                        customSharesCents = customShares,
                        note = item.optString("note", "")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun encodeExpenses(expenses: List<Expense>): String {
    val array = JSONArray()
    expenses.forEach { expense ->
        val customShares = JSONObject()
        expense.customSharesCents.forEach { (personId, amount) ->
            customShares.put(personId, amount)
        }
        array.put(
            JSONObject()
                .put("id", expense.id)
                .put("title", expense.title)
                .put("amountCents", expense.amountCents)
                .put("paidByPersonId", expense.paidByPersonId)
                .put("splitMode", expense.splitMode.name)
                .put("participantIds", JSONArray(expense.participantIds))
                .put("customSharesCents", customShares)
                .put("note", expense.note)
        )
    }
    return array.toString()
}

private fun parseSettlementEntries(raw: String?): List<SettlementEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    SettlementEntry(
                        id = item.getString("id"),
                        fromPersonId = item.optString("fromPersonId", ""),
                        toPersonId = item.optString("toPersonId", ""),
                        amountCents = item.optLong("amountCents", 0L),
                        note = item.optString("note", "")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun encodeSettlementEntries(entries: List<SettlementEntry>): String {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("fromPersonId", entry.fromPersonId)
                .put("toPersonId", entry.toPersonId)
                .put("amountCents", entry.amountCents)
                .put("note", entry.note)
        )
    }
    return array.toString()
}

private fun defaultPeople(): List<Person> {
    return listOf(
        Person(UUID.randomUUID().toString(), "You"),
        Person(UUID.randomUUID().toString(), "Friend")
    )
}

private fun nextEventName(events: List<SplitEvent>): String {
    var index = 1
    while (events.any { it.groupName.equals("Event $index", ignoreCase = true) }) {
        index++
    }
    return "Event $index"
}

private fun parseMoneyToCents(input: String): Long {
    val normalized = input.trim().replace(',', '.')
    val value = normalized.toDoubleOrNull() ?: return 0L
    return (value * 100.0).roundToInt().toLong()
}

private fun formatMoney(cents: Long): String {
    val amount = cents / 100.0
    return if (cents % 100L == 0L) {
        String.format(Locale.US, "%d kr", cents / 100L)
    } else {
        String.format(Locale.US, "%.2f kr", amount)
    }
}

private fun formatEditableMoney(cents: Long): String {
    return if (cents % 100L == 0L) {
        (cents / 100L).toString()
    } else {
        String.format(Locale.US, "%.2f", cents / 100.0)
    }
}

private fun buildSettlementSummary(groupName: String, people: List<Person>, settlements: List<Settlement>): String {
    val safeGroupName = groupName.ifBlank { "Splitter group" }
    if (settlements.isEmpty()) {
        return "$safeGroupName is settled."
    }
    return buildString {
        append(safeGroupName)
        append('\n')
        settlements.forEachIndexed { index, settlement ->
            val fromName = people.firstOrNull { it.id == settlement.fromPersonId }?.name.orEmpty().ifBlank { "Someone" }
            val toName = people.firstOrNull { it.id == settlement.toPersonId }?.name.orEmpty().ifBlank { "Someone" }
            append(fromName)
            append(" pays ")
            append(toName)
            append(' ')
            append(formatMoney(settlement.amountCents))
            if (index != settlements.lastIndex) append('\n')
        }
    }
}

private fun sanitizeMoneyInput(input: String): String = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
private fun sanitizeNameInput(input: String): String = input.filter { it.isLetterOrDigit() || it.isWhitespace() || it in setOf('-', '&', '+') }.trimStart()
private fun sanitizeTitleInput(input: String): String = input.filter { it.isLetterOrDigit() || it.isWhitespace() || it in setOf('-', '&', '/', '+', '.', ',', ':') }.trimStart()
private fun sanitizeNoteInput(input: String): String = input.filter { it.code in 32..126 || it == 'å' || it == 'ä' || it == 'ö' || it == 'Å' || it == 'Ä' || it == 'Ö' }

private fun titleStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    letterSpacing = 0.8.sp
)

private fun valueStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    letterSpacing = 0.5.sp
)

private fun labelStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    letterSpacing = 1.2.sp
)

private fun bodyStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

private fun metaStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)
