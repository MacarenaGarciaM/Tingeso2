import { useEffect, useMemo, useState } from "react";
import { useKeycloak } from "@react-keycloak/web";
import { Box, Paper, Stack, Typography, TextField, MenuItem, Checkbox, FormControlLabel, Button,
  Alert, CircularProgress, Divider, Snackbar, Alert as MuiAlert } from "@mui/material";
import { getMe } from "../services/auth.service";
import { getActiveLoans, returnLoan } from "../services/loan.service";

const todayISO = () => new Date().toISOString().slice(0, 10);
const CLP = new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 });

export default function LoanReturn() {
  const { keycloak } = useKeycloak();
  const rolesRaw = keycloak?.tokenParsed?.realm_access?.roles || [];
  const isAdmin = rolesRaw.map(r => String(r).toUpperCase()).includes("ADMIN"); // 👈

  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true); // indica si está cargando datos
  const [err, setErr] = useState("");
  const [loans, setLoans] = useState([]); // préstamos activos
  const [loanId, setLoanId] = useState("");  // préstamo seleccionado
  const [actualReturnDate, setActualReturnDate] = useState(todayISO());
  const [finePerDay, setFinePerDay] = useState(0);
  const [selectedUser, setSelectedUser] = useState(""); // "" = todos (admin)

  const [states, setStates] = useState({});
  const [repairCosts, setRepairCosts] = useState({}); 
  const [submitting, setSubmitting] = useState(false);

  const [toastOk, setToastOk] = useState(false); 
  const [toastMsg, setToastMsg] = useState("");

  // carga inicial + cada vez que cambia el filtro admin
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        setLoading(true);
        setErr("");
        const u = await getMe();
        if (!alive) return;
        setMe(u);

        // rut = si admin y filtro vacío => undefined (trae TODOS)
        const rut = isAdmin ? (selectedUser || undefined) : u.rut;
        const activos = await getActiveLoans(rut);

        if (!alive) return;
        const list = Array.isArray(activos) ? activos : [];
        console.log("[LoanReturn] activos:", list.length, list);
        setLoans(list);

        if (list.length) {
          setLoanId(String(list[0].id));
          const initial = {};
          list[0].items?.forEach(li => { initial[li.toolId] = "ok"; });
          setStates(initial);
        } else {
          setLoanId("");
          setStates({});
        }
      } catch (e) {
        setErr(e?.response?.data || e.message);
        setLoans([]);
        setLoanId("");
        setStates({});
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [isAdmin, selectedUser]);

  const currentLoan = useMemo(() => loans.find(l => String(l.id) === String(loanId)) || null, [loans, loanId]); //encontrar préstamo actual y memoizar

  useEffect(() => {
    if (!currentLoan) return;
    const initialStates = {};
    const initialCosts  = {};
    currentLoan.items?.forEach(li => {
      initialStates[li.toolId] = "ok";
      initialCosts[li.toolId]  = 0;
    });
    setStates(initialStates);
    setRepairCosts(initialCosts);
  }, [currentLoan]);

  const onToggle = (toolId, value) => { // "ok" | "damaged" | "irreparable"
    setStates(prev => ({ ...prev, [toolId]: value }));
    if (value !== "damaged") {
      setRepairCosts(prev => ({ ...prev, [toolId]: 0 }));
    }
  };

  const onChangeRepair = (toolId, value) => { // actualiza costo reparación
    const n = Math.max(0, parseInt(value || "0", 10) || 0);
    setRepairCosts(prev => ({ ...prev, [toolId]: n }));
  };




  const refreshLoans = async () => {
    const rut = isAdmin ? (selectedUser || undefined) : me?.rut;
    const activos = await getActiveLoans(rut);
    const list = Array.isArray(activos) ? activos : [];
    setLoans(list);
    setLoanId("");
    setStates({});
  };

const onSubmit = async (e) => { // maneja el envío del formulario
  e.preventDefault();
  if (submitting) return;
  setSubmitting(true);
  setErr("");

  // definir fuera del try para tener acceso en catch
  let damaged = [];
  let irreparable = [];
  let damagedCosts = {};

  try {
    if (!loanId) throw new Error("Debes seleccionar un préstamo.");
    if (!actualReturnDate) throw new Error("Debes ingresar la fecha real de devolución.");

    damaged = [];
    irreparable = [];
    damagedCosts = {};

      Object.entries(states).forEach(([id, st]) => {
        const num = Number(id);
        if (st === "damaged") {
          damaged.push(num);
          damagedCosts[num] = Math.max(0, Number(repairCosts[num] || 0));
        }
        if (st === "irreparable") irreparable.push(num);
      });

    if (damaged.some(id => irreparable.includes(id))) {
      throw new Error("Una herramienta no puede ser dañada e irrecuperable a la vez.");
    }



    const payload = { //arma el payload para el backend
      actualReturnDate,
      finePerDay: Number(finePerDay) || 0,
      damaged,
      irreparable,
      damagedCosts,
    };

    console.log("[onSubmit] payload:", payload);

    const updated = await returnLoan(loanId, payload);

    setToastMsg(
      `Devolución registrada. Multa atraso: ${CLP.format(updated.lateFine || 0)} · Cargo de daño: ${CLP.format(updated.damagePenalty || 0)}`
    );
    setToastOk(true);
    await refreshLoans();
  } catch (e2) {
    const raw = e2?.response?.data ?? e2?.message ?? e2;
    const msg = typeof raw === "string" ? raw : JSON.stringify(raw);
    console.error("[returnLoan] error:", msg, "payload=", { damaged, irreparable });
    setErr(msg);
  } finally {
    setSubmitting(false);
  }
};


  if (loading) {
    return (
      <Box sx={{ p: 3, display: "flex", alignItems: "center", gap: 1 }}>
        <CircularProgress size={20} /> Cargando…
      </Box>
    );
  }

  const usersInList = [...new Set(loans.map(l => l.rutUser))];

  return (
    <Box sx={{ p: 3, maxWidth: 900, mx: "auto" }} component="form" onSubmit={onSubmit}>
      <Typography variant="h5" sx={{ mb: 2 }}>Devolución de herramientas</Typography>
      {/* Si hay un error técnico del servidor */}
{err && (
  <Alert severity="error" sx={{ mb: 2 }}>
    {typeof err === "string" ? err : "Ocurrió un error inesperado al cargar los datos."}
  </Alert>
)}

{/* Mensaje informativo cuando no hay datos, pero no es un error de red */}
{!loading && !err && loans.length === 0 && (
  <Alert severity="info" sx={{ mb: 2 }}>
    {isAdmin ? "No se encontraron préstamos activos en el sistema." : "Actualmente no tienes préstamos pendientes de devolución."}
  </Alert>
)}
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack spacing={2}>
          {isAdmin && (
            <TextField
              select
              label="Filtrar por usuario"
              value={selectedUser}
              onChange={(e) => setSelectedUser(e.target.value)}
              fullWidth
            >
              <MenuItem value="">(Todos)</MenuItem>
              {usersInList.map((rut, i) => (
                <MenuItem key={i} value={rut}>{rut}</MenuItem>
              ))}
            </TextField>
          )}

          <TextField
            select
            label="Préstamo activo"
            value={loanId}
            onChange={e => setLoanId(e.target.value)}
            fullWidth
            // Ajuste aquí:
            helperText={
              err 
                ? "Error al conectar con el servicio de préstamos" 
                : (!loans.length ? (isAdmin ? "Sin registros en el sistema" : "No tienes préstamos para devolver") : "Selecciona el folio del préstamo")
            }
          >
            {loans.map(l => (
              <MenuItem key={l.id} value={String(l.id)}>
                #{l.id} — Usuario: {l.rutUser} · Reserva: {l.reservationDate} · Pactada: {l.returnDate} · Total: {CLP.format(l.total || 0)}
              </MenuItem>
            ))}
          </TextField>

          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Fecha real de devolución"
              type="date"
              value={actualReturnDate}
              onChange={e => setActualReturnDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
              required
            />
            <TextField
              label="Multa por día (opcional)"
              type="number"
              inputProps={{ min: 0 }}
              value={finePerDay}
              onChange={e => setFinePerDay(e.target.value)}
              fullWidth
            />
          </Stack>
        </Stack>
      </Paper>

      {!!currentLoan && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>Herramientas del préstamo</Typography>
          <Divider sx={{ mb: 2 }} />
          {!!currentLoan && (
  <Paper sx={{ p: 2 }}>
    <Typography variant="subtitle1" sx={{ mb: 1 }}>
      Herramientas del préstamo
    </Typography>
    <Divider sx={{ mb: 2 }} />

    <Stack spacing={1}>
      {currentLoan.items?.map((li) => {
  const toolId = Number(li.toolId);
  const st = states[toolId] || "ok";

  const idOk  = `tool-${toolId}-ok`;
  const idDam = `tool-${toolId}-damaged`;
  const idIrr = `tool-${toolId}-irreparable`;

  return (
    <Box
      key={toolId}
      sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}
    >
      <Box sx={{ minWidth: 320 }}>
        <b>{li.toolNameSnapshot ?? "Herramienta"}</b> — ID: {toolId}
      </Box>

      <FormControlLabel
        control={
          <Checkbox
            id={idOk}
            checked={st === "ok"}
            onChange={() => onToggle(toolId, "ok")}
          />
        }
        label="OK"
      />

      <FormControlLabel
        control={
          <Checkbox
            id={idDam}
            checked={st === "damaged"}
            onChange={() => onToggle(toolId, "damaged")}
          />
        }
        label="Dañada"
      />

      {st === "damaged" && (
        <TextField
          size="small"
          type="number"
          inputProps={{ min: 0 }}
          sx={{ width: 160 }}
          label="Costo reparación"
          value={repairCosts[toolId] ?? 0}
          onChange={(e) => onChangeRepair(toolId, e.target.value)}
        />
      )}

      <FormControlLabel
        control={
          <Checkbox
            id={idIrr}
            checked={st === "irreparable"}
            onChange={() => onToggle(toolId, "irreparable")}
          />
        }
        label="Irrecuperable"
      />
    </Box>
  );
})}

      {!currentLoan.items?.length && (
        <Typography color="text.secondary">
          Este préstamo no tiene ítems.
        </Typography>
      )}
    </Stack>

    <Stack
      direction="row"
      justifyContent="flex-end"
      spacing={2}
      sx={{ mt: 2 }}
    >
      <Button variant="outlined" onClick={() => setStates({})}>
        Limpiar selección
      </Button>
      <Button type="submit" variant="contained" disabled={!loanId || submitting}>
        {submitting ? "Procesando…" : "Registrar devolución"}
      </Button>
    </Stack>
  </Paper>
)}
        </Paper>
      )}

      <Snackbar open={toastOk} autoHideDuration={3500} onClose={() => setToastOk(false)} anchorOrigin={{ vertical: "bottom", horizontal: "right" }}>
        <MuiAlert onClose={() => setToastOk(false)} severity="success" variant="filled" sx={{ width: "100%" }}>
          {toastMsg || "Devolución registrada"}
        </MuiAlert>
      </Snackbar>
    </Box>
  );
}
