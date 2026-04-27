package com.plainpuffin.splitter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    val context = androidx.compose.ui.platform.LocalContext.current

    var groupName by remember { mutableStateOf(preferences.getString(KEY_GROUP_NAME, "Apartment group") ?: "Apartment group") }
    var people by remember { mutableStateOf(loadPeople(preferences)) }
    var expenses by remember { mutableStateOf(loadExpenses(preferences)) }

    var expenseTitle by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(SplitMode.EQUAL) }
    var paidByPersonId by remember { mutableStateOf(people.firstOrNull()?.id.orEmpty()) }
    var selectedParticipantIds by remember { mutableStateOf(people.map { it.id }.toSet()) }
    var customShareInputs by remember { mutableStateOf(people.associate { it.id to "" }) }

    fun persistCore(updatedGroupName: String = groupName, updatedPeople: List<Person> = people, updatedExpenses: List<Expense> = expenses) {
        groupName = updatedGroupName
        people = updatedPeople
        expenses = updatedExpenses
        preferences.edit()
            .putString(KEY_GROUP_NAME, updatedGroupName)
            .putString(KEY_PEOPLE, encodePeople(updatedPeople))
            .putString(KEY_EXPENSES, encodeExpenses(updatedExpenses))
            .apply()
    }

    fun syncDraftState(updatedPeople: List<Person>) {
        val ids = updatedPeople.map { it.id }
        if (paidByPersonId !in ids) {
            paidByPersonId = ids.firstOrNull().orEmpty()
        }
        selectedParticipantIds = selectedParticipantIds.filter { it in ids }.toMutableSet().ifEmpty { ids.toSet() }
        customShareInputs = ids.associateWith { customShareInputs[it] ?: "" }
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
            Toast.makeText(context, "Keep at least two people in the group.", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedPeople = people.filterNot { it.id == personId }
        val updatedExpenses = expenses.filterNot { it.paidByPersonId == personId || personId in it.participantIds }
        persistCore(updatedPeople = updatedPeople, updatedExpenses = updatedExpenses)
        syncDraftState(updatedPeople)
    }

    fun updatePersonName(personId: String, name: String) {
        updatePeople(people.map { if (it.id == personId) it.copy(name = sanitizeNameInput(name)) else it })
    }

    fun toggleParticipant(personId: String, enabled: Boolean) {
        selectedParticipantIds = if (enabled) selectedParticipantIds + personId else selectedParticipantIds - personId
    }

    fun clearDraft() {
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

    fun addExpense() {
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

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            title = expenseTitle.trim().ifBlank { "Expense" },
            amountCents = amountCents,
            paidByPersonId = paidByPersonId,
            splitMode = splitMode,
            participantIds = participants.map { it.id },
            customSharesCents = customShares,
            note = noteInput.trim()
        )

        persistCore(updatedExpenses = expenses + expense)
        clearDraft()
        Toast.makeText(context, "Expense added.", Toast.LENGTH_SHORT).show()
    }

    val balances = remember(people, expenses) { computeBalances(people, expenses) }
    val settlements = remember(people, balances) { computeSettlements(balances) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SplitterPalette.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderPanel(groupName = groupName, onGroupNameChange = {
            val sanitized = sanitizeTitleInput(it)
            persistCore(updatedGroupName = sanitized.ifBlank { "Apartment group" })
        })

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
            selectedParticipantIds = selectedParticipantIds,
            customShareInputs = customShareInputs,
            onTitleChange = { expenseTitle = sanitizeTitleInput(it) },
            onAmountChange = { amountInput = sanitizeMoneyInput(it) },
            onNoteChange = { noteInput = sanitizeNoteInput(it) },
            onPaidByChange = { paidByPersonId = it },
            onSplitModeChange = { splitMode = it },
            onToggleParticipant = ::toggleParticipant,
            onCustomShareChange = { personId, value -> customShareInputs = customShareInputs + (personId to sanitizeMoneyInput(value)) },
            onAddExpense = ::addExpense
        )

        BalancesPanel(people = people, balances = balances, settlements = settlements)

        ExpensesPanel(
            people = people,
            expenses = expenses,
            onDeleteExpense = { expenseId ->
                persistCore(updatedExpenses = expenses.filterNot { it.id == expenseId })
                Toast.makeText(context, "Expense removed.", Toast.LENGTH_SHORT).show()
            }
        )

        ActionPanel(
            hasExpenses = expenses.isNotEmpty(),
            onClearExpenses = {
                persistCore(updatedExpenses = emptyList())
                Toast.makeText(context, "All expenses cleared.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun HeaderPanel(groupName: String, onGroupNameChange: (String) -> Unit) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Splitter", style = titleStyle(), color = SplitterPalette.Text)
            Text(text = "Local-first expense sharing", style = bodyStyle(), color = SplitterPalette.Subtle)
            StyledTextField(
                value = groupName,
                placeholder = "Group name",
                onValueChange = onGroupNameChange
            )
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
    selectedParticipantIds: Set<String>,
    customShareInputs: Map<String, String>,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPaidByChange: (String) -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onToggleParticipant: (String, Boolean) -> Unit,
    onCustomShareChange: (String, String) -> Unit,
    onAddExpense: () -> Unit
) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "ADD EXPENSE", style = labelStyle(), color = SplitterPalette.Highlight)
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
                    text = "Custom mode requires participant shares to add up exactly to the expense total.",
                    style = metaStyle(),
                    color = SplitterPalette.Subtle
                )
            }

            PrimaryButton(text = "Add expense", onClick = onAddExpense)
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
private fun ExpensesPanel(
    people: List<Person>,
    expenses: List<Expense>,
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
                        SmallButton(text = "Delete", accent = SplitterPalette.Danger, onClick = { onDeleteExpense(expense.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(hasExpenses: Boolean, onClearExpenses: () -> Unit) {
    PixelPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "CONTROLS", style = labelStyle(), color = SplitterPalette.Highlight)
            PrimaryButton(
                text = "Clear all expenses",
                onClick = onClearExpenses,
                accent = SplitterPalette.PanelAlt,
                enabled = hasExpenses
            )
        }
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
            contentColor = SplitterPalette.Background,
            disabledContainerColor = SplitterPalette.PanelAlt,
            disabledContentColor = SplitterPalette.Subtle
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text = text, style = bodyStyle(), modifier = Modifier.padding(vertical = 4.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallButton(text: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = SplitterPalette.Background),
        shape = RoundedCornerShape(8.dp)
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
            contentColor = if (selected) SplitterPalette.Background else SplitterPalette.Text
        ),
        shape = RoundedCornerShape(8.dp)
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
            contentColor = if (selected) SplitterPalette.Background else SplitterPalette.Text
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = text, style = bodyStyle())
    }
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

private fun computeBalances(people: List<Person>, expenses: List<Expense>): Map<String, Long> {
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

private fun loadPeople(preferences: SharedPreferences): List<Person> {
    val stored = preferences.getString(KEY_PEOPLE, null)
    val parsed = parsePeople(stored)
    return if (parsed.isEmpty()) {
        listOf(
            Person(UUID.randomUUID().toString(), "You"),
            Person(UUID.randomUUID().toString(), "Friend")
        )
    } else {
        parsed
    }
}

private fun loadExpenses(preferences: SharedPreferences): List<Expense> = parseExpenses(preferences.getString(KEY_EXPENSES, null))

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
                            for (index in 0 until participantsArray.length()) {
                                add(participantsArray.getString(index))
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
