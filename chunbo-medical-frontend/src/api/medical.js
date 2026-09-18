import axios from 'axios'

// 患者相关
export const getPatients = () => axios.get('/api/patients')
export const getPatientDetail = (id) => axios.get(`/api/patients/${id}`)
export const addPatient = (data) => axios.post('/api/patients', data)
export const updatePatient = (id, data) => axios.put(`/api/patients/${id}`, data)

// 药品与处方
export const getMedicines = () => axios.get('/api/medicines')
export const getPrescriptions = (patientId) => axios.get('/api/prescription/patient-history', { params: { patientId } })
export const createPrescription = (data) => axios.post('/api/prescription/create', data)

// 智慧药房闭环
export const getPharmacyPrescriptions = (status) => axios.get('/api/pharmacy/prescriptions', { params: { status } })
export const dispensePrescription = (id) => axios.post(`/api/pharmacy/dispense/${id}`)

// AI 模型设置
export const getAiConfig = () => axios.get('/api/settings/ai')
export const updateAiConfig = (data) => axios.post('/api/settings/ai', data)
export const testAiConfig = (data) => axios.post('/api/settings/ai/test', data)
