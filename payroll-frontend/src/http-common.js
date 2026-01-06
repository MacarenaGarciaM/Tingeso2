import axios from "axios";

const payrollBackendServer = import.meta.env.VITE_PAYROLL_BACKEND_SERVER; //Local host
const payrollBackendPort = import.meta.env.VITE_PAYROLL_BACKEND_PORT; //8090

console.log(payrollBackendServer)
console.log(payrollBackendPort)

export default axios.create({
    baseURL: `http://${payrollBackendServer}:${payrollBackendPort}`, //URL base para todas las peticiones
    headers: {
        'Content-Type': 'application/json'
    }
});