import React, { useState, useRef, useEffect } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { FileUpload } from 'primereact/fileupload';
import { Toast } from 'primereact/toast';
import { Tag } from 'primereact/tag';
import { useAuth } from '../../contexts/AuthContext';
import { EmpresaLogoService } from '../../services/EmpresaLogoService';
import './EmpresaLogo.css';

export const EmpresaLogo = () => {
    const { user } = useAuth();
    const toast = useRef(null);
    const [loading, setLoading] = useState(false);
    const [selectedFile, setSelectedFile] = useState(null);
    const [logoUrl, setLogoUrl] = useState(null);
    const [hasLogo, setHasLogo] = useState(false);

    useEffect(() => {
        if (user?.empresaId) {
            checkLogo();
        }
    }, [user]);

    const checkLogo = async () => {
        try {
            // Verificar se existe logo
            setHasLogo(true);
        } catch (err) {
            setHasLogo(false);
        }
    };

    const handleFileSelect = (e) => {
        setSelectedFile(e.files[0]);
    };

    const uploadLogo = async () => {
        if (!selectedFile) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Aviso',
                detail: 'Selecione um arquivo de imagem',
                life: 3000
            });
            return;
        }

        setLoading(true);
        try {
            await EmpresaLogoService.uploadLogo(user?.empresaId, selectedFile);
            toast.current?.show({
                severity: 'success',
                summary: 'Sucesso',
                detail: 'Logo da empresa atualizado com sucesso',
                life: 3000
            });
            setSelectedFile(null);
            setHasLogo(true);
        } catch (err) {
            toast.current?.show({
                severity: 'error',
                summary: 'Erro',
                detail: err.message,
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const removerLogo = async () => {
        setLoading(true);
        try {
            await EmpresaLogoService.deleteLogo(user?.empresaId);
            toast.current?.show({
                severity: 'success',
                summary: 'Sucesso',
                detail: 'Logo da empresa removido com sucesso',
                life: 3000
            });
            setHasLogo(false);
            setLogoUrl(null);
        } catch (err) {
            toast.current?.show({
                severity: 'error',
                summary: 'Erro',
                detail: err.message,
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="empresalogo-enterprise-container">
            <Toast ref={toast} />
            
            <Card title="Logo da Empresa" className="empresalogo-main-card">
                <div className="empresalogo-header-actions mb-4">
                    <p className="text-muted m-0">Gerencie o logo da sua empresa. O logo sera exibido em documentos e relatorios.</p>
                </div>

                <div className="empresalogo-content p-fluid">
                    {hasLogo ? (
                        <div className="empresalogo-preview flex flex-column align-items-center justify-content-center p-5 border-round bg-gray-100 mb-4">
                            <img 
                                src={logoUrl || '/assets/layout/images/logo-dark.svg'} 
                                alt="Logo da Empresa"
                                className="empresalogo-image mb-3"
                                style={{ maxWidth: '200px', maxHeight: '200px' }}
                            />
                            <Tag value="Logo atual" severity="success" />
                        </div>
                    ) : (
                        <div className="empresalogo-empty flex flex-column align-items-center justify-content-center p-5 border-round bg-gray-100 mb-4">
                            <i className="pi pi-image text-4xl text-gray-400 mb-3" />
                            <span className="text-gray-500">Nenhum logo configurado</span>
                        </div>
                    )}

                    <div className="field">
                        <label className="font-bold mb-2 block">Selecionar Novo Logo</label>
                        <FileUpload
                            mode="basic"
                            name="logo"
                            accept="image/*"
                            maxFileSize={5000000}
                            onSelect={handleFileSelect}
                            onClear={() => setSelectedFile(null)}
                            chooseLabel="Selecionar Imagem"
                            className="empresalogo-upload"
                        />
                        {selectedFile && (
                            <div className="mt-2">
                                <Tag value={selectedFile.name} severity="info" />
                                <span className="ml-2">({(selectedFile.size / 1024 / 1024).toFixed(2)} MB)</span>
                            </div>
                        )}
                    </div>

                    <div className="empresalogo-actions flex gap-2 mt-4">
                        <Button
                            label="Enviar Logo"
                            icon="pi pi-upload"
                            onClick={uploadLogo}
                            loading={loading}
                            disabled={!selectedFile}
                            className="p-button-success"
                        />
                        {hasLogo && (
                            <Button
                                label="Remover Logo"
                                icon="pi pi-trash"
                                onClick={removerLogo}
                                loading={loading}
                                className="p-button-danger"
                            />
                        )}
                    </div>

                    <div className="empresalogo-recomendacoes mt-4 p-3 bg-gray-100 border-round">
                        <h5>Recomendacoes:</h5>
                        <ul className="text-muted m-0 pl-3">
                            <li>Tamanho ideal: 200x200 pixels</li>
                            <li>Formato: PNG ou JPEG</li>
                            <li>Tamanho maximo: 5MB</li>
                            <li>Fundo transparente recomendado</li>
                        </ul>
                    </div>
                </div>
            </Card>
        </div>
    );
};

export default EmpresaLogo;
